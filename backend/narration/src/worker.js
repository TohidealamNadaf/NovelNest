// Workers Free only. No paid fallback, external model key, storage product, or scheduled generation.
const MODEL = "@cf/myshell-ai/melotts";
const MAX_BODY = 8192;
const MAX_TEXT = 700;
const MAX_AUDIO = 8 * 1024 * 1024;
const recent = new Map(); // Short-lived per-isolate deduplication; not a durable global job queue.

function error(message, status, code = "REQUEST_FAILED") {
  return Response.json({ error: message, code }, { status, headers: { "Cache-Control": "no-store" } });
}

class AudioFailure extends Error {
  constructor(message, code, status = 502) { super(message); this.code = code; this.status = status; }
}

function decodeAudioPayload(payload) {
  const encoded = payload?.audio ?? payload?.result?.audio;
  if (typeof encoded !== "string" || !encoded.length || encoded.length > MAX_AUDIO * 1.4)
    throw new AudioFailure("Unexpected model audio schema", "UPSTREAM_FORMAT");
  // Accept raw base64 and a bounded audio data URL; never fetch a returned arbitrary URL.
  const base64 = encoded.startsWith("data:audio/") ? encoded.slice(encoded.indexOf(",") + 1) : encoded;
  try { return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0)); }
  catch { throw new AudioFailure("Invalid base64 model audio", "UPSTREAM_FORMAT"); }
}

function audioType(bytes) {
  const matches = (offset, text) => Array.from(text).every((c, i) => bytes[offset + i] === c.charCodeAt(0));
  if (bytes.length >= 12 && matches(0, "RIFF") && matches(8, "WAVE")) return "audio/wav";
  if (bytes.length >= 3 && (matches(0, "ID3") ||
      (bytes[0] === 255 && (bytes[1] & 224) === 224 && (bytes[1] & 6) !== 0))) return "audio/mpeg";
  throw new AudioFailure("Model did not return playable audio", "INVALID_AUDIO");
}

async function readBounded(stream, limit) {
  const reader = stream.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > limit) { await reader.cancel(); throw new Error("Payload too large"); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return bytes;
}

async function authorized(request, env) {
  if (!env.NARRATION_ACCESS_TOKEN || env.NARRATION_ACCESS_TOKEN.length < 24) return false;
  const digest = async (s) => new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));
  const [actual, expected] = await Promise.all([
    digest(request.headers.get("Authorization") || ""), digest(`Bearer ${env.NARRATION_ACCESS_TOKEN}`)
  ]);
  let diff = 0;
  for (let i = 0; i < actual.length; i++) diff |= actual[i] ^ expected[i];
  return diff === 0;
}

async function synthesize(text, env) {
  // Preserve the model's binary response rather than relying on implicit SDK JSON conversion.
  const result = await env.AI.run(MODEL, { prompt: text, lang: "en" }, { returnRawResponse: true });
  let bytes;
  if (result instanceof Response) {
    if (!result.ok || !result.body) throw new AudioFailure("Upstream audio unavailable",
      result.status === 429 ? "QUOTA_EXCEEDED" : "UPSTREAM_UNAVAILABLE", result.status === 429 ? 429 : 502);
    if ((result.headers.get("Content-Type") || "").includes("json")) {
      const body = await readBounded(result.body, Math.ceil(MAX_AUDIO * 1.4));
      let payload;
      try { payload = JSON.parse(new TextDecoder().decode(body)); }
      catch { throw new AudioFailure("Invalid model response", "UPSTREAM_FORMAT"); }
      bytes = decodeAudioPayload(payload);
    } else bytes = await readBounded(result.body, MAX_AUDIO);
  } else if (result instanceof ReadableStream) bytes = await readBounded(result, MAX_AUDIO);
  else if (result instanceof ArrayBuffer) bytes = new Uint8Array(result);
  else if (ArrayBuffer.isView(result)) bytes = new Uint8Array(result.buffer, result.byteOffset, result.byteLength);
  else {
    bytes = decodeAudioPayload(result);
  }
  if (!bytes.length || bytes.length > MAX_AUDIO) throw new Error("Invalid audio length");
  return { bytes, contentType: audioType(bytes) };
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const health = url.pathname === "/v1/narration/health";
    if (url.pathname !== "/v1/narration" && !health) return error("Not found", 404);
    if (request.method !== (health ? "GET" : "POST")) return error("Method not allowed", 405);
    if (!await authorized(request, env)) return error("Unauthorized", 401);
    if (!env.REQUEST_LIMIT || !env.AI) return error("Worker bindings are not configured", 503, "CONFIGURATION");
    // Personal deployment: one provisioned credential. Regional limiter, not a global billing ledger.
    const limited = await env.REQUEST_LIMIT.limit({ key: "personal-narration" });
    if (!limited.success) return error("Too many requests", 429, "RATE_LIMITED");
    if (health) return Response.json({ service: "novelnest-narration", protocol: 1,
      ready: !!env.AI && !!env.REQUEST_LIMIT, model: MODEL }, { headers: { "Cache-Control": "no-store" } });
    if (!(request.headers.get("Content-Type") || "").startsWith("application/json")) return error("JSON required", 415);
    if (!request.body || Number(request.headers.get("Content-Length")) > MAX_BODY) return error("Invalid request size", 413);
    let payload;
    try { payload = JSON.parse(new TextDecoder().decode(await readBounded(request.body, MAX_BODY))); }
    catch { return error("Invalid JSON or request too large", 400); }
    const text = payload?.text;
    if (typeof text !== "string" || !text.trim() || text.length > MAX_TEXT) return error("Text must contain 1–700 characters", 400);
    for (const [key, entry] of recent) if (entry.expires < Date.now()) recent.delete(key);
    const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${MODEL}|en|${text}`));
    const key = Array.from(new Uint8Array(hash), (b) => b.toString(16).padStart(2, "0")).join("");
    let entry = recent.get(key);
    if (!entry) {
      if (recent.size >= 2) {
        const completed = Array.from(recent).find(([, value]) => value.completed);
        if (completed) recent.delete(completed[0]);
        else return error("Service busy; retry later", 429, "BUSY");
      }
      entry = { expires: Date.now() + 120_000, completed: false, promise: null };
      entry.promise = synthesize(text, env).then((audio) => {
        entry.completed = true;
        entry.expires = Date.now() + 60_000;
        return audio;
      });
      recent.set(key, entry);
    }
    try {
      const audio = await entry.promise;
      return new Response(audio.bytes, { headers: { "Content-Type": audio.contentType, "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" } });
    } catch (e) {
      recent.delete(key);
      if (e instanceof AudioFailure) return error(e.message, e.status, e.code);
      const message = String(e?.message || "");
      const quota = /quota|neuron|daily limit|429/i.test(message);
      return error("Cloud narration unavailable", quota ? 429 : 503, quota ? "QUOTA_EXCEEDED" : "UPSTREAM_UNAVAILABLE");
    }
  }
};
