# AI quota and cache audit — 2026-10-25

## Findings and fixes

- Android production wiring used nanoTime for quota windows and model-directory TTL. That clock excludes deep sleep.
  Both now use injected SystemClock.elapsedRealtime, so screen-off device sleep advances quota expiry. Pure JVM
  defaults/test clocks remain platform independent. [Android clock guidance](https://developer.android.com/reference/android/os/SystemClock).
- The local limiter grouped all Groq models under one budget. Curated Groq routes now have separate model budgets;
  actual organization/project limits remain provider-enforced. Known model quota failures can advance to another
  explicitly configured model. Unknown/shared limits stop attempts under that provider.
- Local admission/cooldown exceptions were fed back into remote cooldown bookkeeping. Rounded retry seconds could
  move expiry during repeated checks. Only actual provider calls create remote cooldowns; local rejections are marked
  and ignored by cooldown updates. Original expiry is retained without polling or automatic retries.
- Provider errors hid daily versus minute limits. Groq TPM/RPM/TPD/RPD errors now have safe, specific messages.
  Retry-After and bounded server duration hints are honored. Raw provider messages, organization IDs, chapter text
  and credentials are never displayed or retained in quota state.
- Successful response quota headers were discarded. Groq remaining tokens and remaining DAILY requests now have
  independent reset times. Headers already include earlier consumption, so local reservations are not double charged.
  Later ambiguous requests retain their reservations, including daily consumption after the minute window expires.
- Explicit Retry-After takes precedence over a counter's full reset duration. A failed call's old depleted snapshot is
  retired when its remote cooldown is recorded, so it cannot re-block the route after that explicit retry time expires.
- Quota state contains only fixed provider/model identifiers, counters, bounded recent reservations and reset times.
  Unrecognized model strings cannot create unbounded quota keys. There is no timer, quota-refresh loop or background job.
- An explicitly replaced credential retires the previous account's process-local quota. Only one SHA-256 fingerprint
  per provider is retained. Credential epochs fence late quota/usage feedback from the previous account. Rebinding the
  same key preserves cooldowns. Cancelled response bodies close without publishing new quota state.
- The result cache removed corrupt JSON but left oversized entries and temporary files from interrupted writes.
  First use now recovers owned temporary/oversized files and reapplies the 96-file/16-MiB limit before generation.
  Memory remains an LRU of four validated results. Cache reads reject mismatched input/action keys.
- Cache cancellation is rethrown; response streams and temporary files are closed/cleaned on failure paths. Cache
  generation epochs still prevent an in-flight generation from repopulating a cleared cache. Cache I/O failures remain
  disposable and do not erase durable notes or term discoveries.

## Limits of this diagnosis

The user identified Groq GPT-OSS 120B, GPT-OSS 20B and Qwen 3.8 27B, but no exact failed response or device trace was
available for this audit. These are established code defects; the specific account's limiting dimension is not proven.
The user subsequently identified a per-minute error. Without its requested/limit counters, consumed minute capacity
and an individually oversized request cannot be distinguished for that specific chapter. HTTP 413 and Groq 429
responses whose requested tokens exceed the entire TPM limit now become TOO_LARGE, not a renewable cooldown.
New errors distinguish it using provider feedback. No live inference, account inspection, Android heap dump or device
thermal profile was performed. Bounded caches and cancellation ownership are not proof that the entire app has no leaks.

Current Groq documentation lists each curated model at 30 RPM, 1,000 RPD, 8K TPM and 200K TPD. Organization/project
overrides and separate input/output limits can differ. Exceeding a daily allowance is not repaired by waiting one minute.
Source: [Groq limits](https://console.groq.com/docs/rate-limits), [projects](https://console.groq.com/docs/projects).

### Request size is separate from quota expiry

An oversized whole-chapter request returns TOO_LARGE, not a timed quota rejection. Waiting does not make it fit.
All three curated Groq models have the same configured 8K quick-request budget. Another Groq model can help with
model-specific used allowance, but cannot increase that per-request budget. A configured larger-capacity route is
required for such chapters. The app never silently drops source paragraphs to force a request through.
Summary and extraction are separate operations and consume separate requests; only the same source/action/topic
can reuse a validated cached result. Seeing two operations consume allowance is not evidence of a cache leak.

## Free API research

No suitable managed API with a verified commitment to permanent, unlimited, free text inference was established.
Free model weights and free inference hosting are separate things.

| Service | Verified position | Implication |
| --- | --- | --- |
| Groq | Free quotas are model/account limited by minute and day. | Current integration can respect/reset/report limits; it cannot remove them. |
| Z.ai | GLM-4.7-Flash and GLM-4.5-Flash are listed with free token pricing. Account limits are available after sign-in; request throttling/overload are documented. | A candidate free-priced fallback, not a verified unlimited service. Not integrated by this audit. |
| Gemini | Model/project RPM, TPM and RPD limits; active values are in AI Studio. | Free eligible models remain quota limited. |
| OpenRouter | Free-model daily/minute caps and upstream capacity limits. | A `:free` suffix is a price designation, not unlimited throughput. |
| Hugging Face | Free user monthly inference credits, currently $0.10 subject to change. | Experimentation allowance, not unlimited hosting. |
| Cerebras | Current documentation: $5 credit trial, 30-day expiry, verified payment method; no permanently renewing free tier. | Does not meet ongoing zero-cost access. Older free-tier pages are outdated. |
| Pollinations | Secret keys list no request-rate cap, but generation uses Pollen balances/budgets; terms allow restrictions. | “No rate limit” alone does not establish unlimited free generation. |
| Self-hosting | Inference on an owned computer can avoid an API vendor's quotas. | Requires available hardware, electricity, uptime and secure remote access; no free cloud capacity guarantee. |

Primary sources, checked on the audit date:

- [Z.ai pricing](https://docs.z.ai/guides/overview/pricing), [limits](https://docs.z.ai/api-reference/rate-limit), [errors](https://docs.z.ai/api-reference/api-code)
- [Gemini limits](https://ai.google.dev/gemini-api/docs/rate-limits)
- [OpenRouter limits](https://openrouter.ai/docs/api/reference/limits)
- [Hugging Face pricing](https://huggingface.co/docs/inference-providers/pricing)
- [Cerebras current limits and trial FAQ](https://inference-docs.cerebras.ai/support/rate-limits)
- [Pollinations API documentation](https://gen.pollinations.ai/), [terms](https://enter.pollinations.ai/terms)

## Verification scope

Offline regressions cover cooldown expiry under repeated checks, model-scoped fallback, shared-limit blocking,
minute versus daily counters, actual usage settlement, server reset parsing, unknown daily consumption, response-body
closure, cache memory eviction, startup count/byte caps, abandoned/oversized files, clear epochs and source/key isolation.
The existing rewrite, summary, extraction, cancellation, saved-term and settings suites are also run. Provider quality,
the user's actual quota values and device heap/temperature still require live/device evidence.

Final verification: `:app:testDebugUnitTest assembleDebug --offline --warning-mode fail --max-workers=1` succeeded.
269 unit cases: 266 passed, 3 opt-in/live cases skipped, no failures/errors. The debug APK is
`app/build/outputs/apk/debug/app-debug.apk`. No live provider calls were made during this audit.
