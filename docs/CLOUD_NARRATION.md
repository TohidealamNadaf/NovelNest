# Cloud narration: Cloudflare Workers Free + MeloTTS

## Setup (required before Listen works)

This repository contains the Android client and a deployable Worker. No remote service is deployed or account created by the Android app. Speech is generated on Cloudflare; there is no local TTS fallback and no Cloudflare account token in the APK.

1. Create/sign in to a Cloudflare account. Keep **Workers Free**, not Workers Paid. Verify the current Workers AI free allowance and that MeloTTS is available to the account. Do not enable prepaid AI Gateway billing or other paid products.
2. In `backend/narration`, install the development dependency with `npm install`, then run `npx wrangler login`.
3. Create a random access token with at least 32 random bytes, using a password manager. Run `npx wrangler secret put NARRATION_ACCESS_TOKEN` and enter it at the prompt. This is a private Worker credential, **not** a Cloudflare account API token. Never put it in source control.
4. Run `npm run deploy`. The Worker uses an AI binding, a rate-limiting binding, and no database, R2 bucket, cron, or custom domain.
5. Open Settings → Audio Narration. Enter `https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev/v1/narration` and the private Worker access token. Tap Save Configuration, then Check Endpoint. Listen is available in the reader's TT settings sheet. The existing reader Setup action can also save credentials.

Check Endpoint calls authenticated `GET /v1/narration/health`. Deploy the updated Worker to expose this route. The check confirms authentication, the protocol, and configured bindings; it does not run MeloTTS, consume synthesis allowance, verify account billing tier, or prove remaining AI quota. Saved configuration is not labeled as a successful connection.

The Settings page scans and clears the same `narration_audio` directory as playback, off the UI thread. Refresh Cache retrieves a fresh file count. Clearing reports actual remaining files and deletion failures. While a narration service exists (including its paused grace period), the audio cache is preserved; press Stop before clearing. The general Storage & Cache action uses this same protection. Credential decryption and encryption run off the UI thread. One credential snapshot is used for each audio request, so endpoint edits cannot mix old/new credentials or cache identities mid-request.

Deployment requires account authentication and is a separate external action. The code does not change subscription plans. Workers Free requests stop at the provider allowance; upgrading the account to Paid changes that billing behavior. Check account status in the dashboard, not a flag in the APK.

## Boundaries and privacy

- This is a **personal/trusted-device deployment**. The access token is manually provisioned and encrypted with an Android Keystore AES-GCM key; its preference file is excluded from backup/transfer. Rotate the Worker secret if a device/token is compromised. Restoring onto a new phone requires setup again.
- Do not publish a shared access token to arbitrary app users. Public distribution requires individual authentication/provisioning and revocation before enabling shared cloud compute.
- Each requested passage is sent over HTTPS to the configured Worker and Cloudflare Workers AI. The setup UI states this. No chapter text is logged by our Worker; Worker observability is disabled. This does not override Cloudflare's own processing/data terms.
- Only the fixed English MeloTTS model can be called. Clients cannot choose a more expensive model or submit arbitrary model parameters.
- Provider quota is shared across the Cloudflare account. There is no unlimited-free promise. Check current pricing: https://developers.cloudflare.com/workers-ai/platform/pricing/

## Android implementation

- Platform `MediaPlayer` and `MediaSession` keep the dependency footprint small. A foreground `mediaPlayback` service owns playback; the Compose reader never owns a player, network call, or engine.
- Passages are split off the main thread at sentence boundaries, maximum 700 UTF-16 code units, normally grouped below 450. No book-sized request, raw PCM buffer, local synthesis, LLM, or continuous UI progress polling.
- One foreground download or one next-passage prefetch at a time. The prefetch starts only after the current MP3 starts playing. Pausing cancels pending work; a provider request already accepted may still consume allowance.
- MP3 response bodies stream to temporary disk files in bounded 8-KB buffers. Responses are capped at 8 MB, redirects disabled, HTTPS required. Successful downloads are renamed into cache. Network bodies close on every path and cancellation cancels the underlying call.
- A dedicated 64-MiB disposable cache is keyed by endpoint, text, and pipeline/model version. It does not evict the existing reader text cache. Active/prefetched files are protected during eviction. Android may clear disposable cache; it is not an explicit offline download library.
- Save listening position every 5 seconds and on pause/stop/chapter change; do not write on every playback frame. Listening checkpoints do not overwrite visual reader scroll progress. Text fingerprints prevent restoring a position into changed chapter text.
- No automatic retries, cloud keep-alives, boot playback, process-death restart, background chapter generation, or paid/local fallback. Quota/unavailable states pause with an actionable message.

## Lifecycle behavior

| Event | Behavior |
|---|---|
| Lock screen, Home key, another app, navigation away | Continue active playback with media notification controls |
| Remove task from Recents | Continue if playing/buffering; stop if paused |
| Notification Stop / Android force-stop / reboot | Stop; no automatic restart |
| Process killed | Resume from saved position only after a new explicit Listen action |
| Normal launcher open | Existing Home-first launch behavior retained |
| Notification body tap | Open app normally; playback remains controlled by media notification or reader sheet |
| Headphones unplug / Bluetooth noisy broadcast | Pause |
| Transient audio focus loss | Pause; resume on focus return only if no explicit pause superseded it |
| Permanent focus loss | Pause until user resumes |
| Pause for 60 seconds | Save position, release decoder and foreground priority; retain paused player while the service remains alive |
| Chapter end | Stop at the end; user selects the next chapter explicitly |
| Another chapter opened | Current narration continues until Listen starts the newly selected chapter |
| Network unavailable | Cached passages play; missing audio pauses without retry loops |

OEM battery restrictions and force-stop can terminate services. A foreground service is not a guarantee against process death. `MediaPlayer` holds a partial wake lock only for active playback, not when idle; synthesis/downloads are bounded asynchronous work.

## Worker safeguards

`POST /v1/narration`, bearer authentication, 8-KiB bounded input, fixed model/language, and 700-character maximum. A regional approximate limiter allows 12 requests/minute for the provisioned deployment. It is **not** a durable global quota counter. Upstream Workers Free quota is the actual no-paid-overage boundary.

The Worker retains at most two recent response promises per isolate for short-lived duplicate suppression. Completed entries can be evicted immediately to make room. This is not durable idempotency: retries after isolate restart can regenerate audio, which is why the client does not retry ambiguous failures automatically. Private outputs are never placed in a public CDN cache.

## Verification and remaining scope

### Build and install diagnostics

- Narration adds no on-device speech model, native speech SDK, Gradle network task, or build-time Worker request. Cloud generation runs only after an explicit Listen action.
- APK ABI splitting is now opt-in: ordinary `:app:assembleDebug` produces one `app-debug.apk`. To produce the previous four architecture APKs plus a universal APK, use `:app:assembleRelease -PsplitApks=true`. Android bundles continue to support device-specific delivery.
- Gradle task caching is enabled and workers are bounded to two to reduce concurrent memory pressure. Keep the IDE and terminal on the same Gradle JDK and user-home cache; do not routinely run `clean` or launch competing builds. The inspected daemon status included a daemon stopped to reclaim physical memory, but this alone does not establish the cause of an install delay.
- Use `:app:assembleDebug --profile` to separate build time from installation time. The investigation's initial unchanged build succeeded in 9 seconds with all 37 tasks up to date. A first build after configuration/source changes is not comparable to that incremental baseline.
- The updated source/configuration build succeeded in 2m52s: project configuration took approximately 50s, dex generation 47s, Kotlin compilation 32s, KSP 18s, and APK packaging 5s (some task work can overlap). The subsequent unchanged build completed in 8s with all 37 tasks up to date. These measurements confirm incremental work is skipped; they do not establish a device-install speedup. Use the new `app/build/outputs/apk/debug/app-debug.apk`, rather than an older architecture/universal APK from a previous build.
- No Android device was attached during this investigation. APK transfer, Android package verification/optimization, storage pressure, and actual device crash/ANR logs still require a connected device; install performance is not verified here.
- Playback now re-enters foreground mode before acquiring audio focus through system media callbacks, releases failed native players before retry, and closes its HTTP resources when the service is destroyed. Unexpected background/control failures log only their class and playback stage under `NovelNestNarration`, without logging credentials or chapter text.

Kotlin compilation is used during implementation; automated tests and live cloud inference require separate authorization/configuration. No phone is currently profiled, so there is no claim of zero heat: local neural generation is eliminated, but audio decoding, radio use, and the display still consume power.

Before release, exercise real MP3 output against the deployed Worker, quotas, initialization pause/resume, lock screen, focus/calls, Bluetooth, Recents, process kill, failed networks, cache eviction, and long chapters. Measure time to first audio and device temperature during sustained playback.

Playback speed is available in Reader → TT settings → Cloud Narration → Speed (0.75×, 1×, 1.25×, 1.5×, 1.75×, 2×). It is saved on the device and reused for every passage and listening session. Android changes the downloaded audio's playback speed with pitch set to 1; it does not regenerate audio, change cache keys, or consume additional synthesis allowance for that audio. Faster listening can reach uncached passages sooner. Changing speed while paused does not resume playback. Unsupported decoder speeds fall back to 1× with a message. Listening checkpoints remain positions in the original audio, independent of playback speed.

Voice remains the fixed default English MeloTTS voice; there is no voice selector in this pipeline.

Android's system media progress bar uses the prepared MP3 passage's actual duration, timestamped position, and playback speed. It supports seeking within that passage, including while paused. Position is resynchronized after a seek completes and on the existing 5-second checkpoint cadence without rebuilding notifications every tick. The timeline resets at each passage; the full chapter duration is unknown until all its audio has been generated, so the app does not report an estimated chapter length as exact audio duration. Previous/next actions still navigate passages.

First version deliberately stops at chapter end. Auto-next-chapter, sleep timers, voice selection, music mixing, permanent downloads, and public-user identity provisioning are not implemented in this version.

## Shared in-app mini-player

Quick Settings keeps its Play action and dismisses the sheet after a successful playback command. The progress card is mounted once in `NovelNestApp`, across Home, Discover, Browse, Search, Settings, novel details, and the reader. It sits above the footer where present, and respects system navigation insets elsewhere. Its measured height is reserved from page content so it does not cover the last list item or reader text. Dialogs and modal sheets may temporarily cover it; the media notification still controls the same service.

The mini-player provides pause/resume, previous/next **audio passage**, speed selection, seeking within the current MP3, and a cross labeled Stop narration and close player. Closing sends the service's Stop action, saves the checkpoint, cancels outstanding work, removes the notification, and releases playback resources. Pause preserves the player. Normal navigation never starts a second player or restarts audio, and no floating-window/overlay permission is needed.

Position snapshots reuse the media session's existing 5-second checkpoint updates. Only the seek-bar leaf interpolates progress while the UI is started; backgrounding cancels that animation. The app root observes only session visibility, so position updates do not recompose the navigation host. No per-page timers, audio fetching, or additional synthesis are introduced. After prolonged pause, resuming promotes the service and reopens the cached passage at the saved offset. Android may still kill a background paused service; it does not auto-resume after process death or force-stop.

## Diagnosing preparation and playback failures

The player reports the current stage: loading chapter text, checking/opening cached audio, connecting, waiting for Cloudflare audio, downloading, opening the device player, playing, paused, failed, or complete. Waiting includes any provider queuing/inference; there is no remote job-progress API, so the app does not invent generation percentages. Failures stay visible as failures with an actionable message, rather than a generic Paused label. The paused resource-release timer preserves that message.

Clearing the disposable cache does not remove credentials or disable narration. It requires fresh cloud generation for passages that were removed, subject to connectivity, account allowance, and Worker availability. Playback position is restored against the available chapter; playback does not require an old audio file to exist.

Redeploy the Worker after these changes. It requests the model's raw response, normalizes binary and JSON/base64 envelopes, verifies MP3/WAV signatures, and labels the actual audio format. Invalid output is returned as a structured error, never mislabeled as an MP3. The Android client checks cached/downloaded headers before playback and replaces invalid cached files. Decoder failures evict the offending file, expose numeric player error codes, and release the player. Asynchronous local-player preparation has a bounded 15-second timeout. User retries remain explicit; there are no automatic synthesis loops.

A successful `MediaPlayer.start()` establishes the playing session; the UI no longer permanently labels it Paused because an immediate native `isPlaying` query lagged startup. Full device playback and live model output still require a connected device/deployed Worker to verify. A non-sensitive `NovelNestNarration` warning records the failure stage, never credentials or chapter text.
