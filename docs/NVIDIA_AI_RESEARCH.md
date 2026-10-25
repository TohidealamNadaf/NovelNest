# NVIDIA hosted AI API research — 2026-10-25

## What was verified

- NVIDIA advertises free access to hosted NIM APIs for **unlimited prototyping** through its Developer Program.
  This is a real free development offering, not merely the old fixed-credit signup description.
- Unlimited prototyping does not remove rate limits. NVIDIA's published FAQ/moderator guidance says limits vary
  by model, use case and traffic; account-specific limits should be checked in the API Catalog. No universal
  request/token allowance or production availability guarantee was established.
- The free access is for prototyping, development, research and testing. NVIDIA's FAQ distinguishes production,
  including non-testing use serving real end users, and directs that use to the appropriate licensed/hosted deployment.
- The hosted text API is `https://integrate.api.nvidia.com/v1/chat/completions`. The official catalog lists text
  candidates including `meta/llama-3.3-70b-instruct`, `nvidia/nemotron-3-nano-30b-a3b` and
  `nvidia/nemotron-3-super-120b-a12b`. Listing is not authenticated availability or chapter-generation verification.

## Fit for Novel Nest

Worth evaluating for development: it can supply a hosted alternative to Groq. It must be labeled as a rate-limited
developer/prototype service, not as permanently free unrestricted inference for a released reading app. A provider
integration needs fixed endpoints/model IDs, the existing encrypted-key storage, sharing consent, cost-policy gating,
bounded requests, output/evidence validation and tested failure handling. No account was created. The initial research
sent no generation requests; subsequent authorized live results are recorded in [AI route verification](AI_ROUTE_VERIFICATION.md).

The user confirmed prototype/testing use. The integration is implemented under **Free account providers**, requiring
the existing account-policy acknowledgement and explicit NVIDIA chapter-sharing consent. It is not included in
**Explicit free only**, and no saved routes or credentials are changed automatically.

## Implemented setup and limits

1. Open Settings > AI Reading Tools and select **Free account providers**.
2. Create a developer API key at https://build.nvidia.com/ and save it in **NVIDIA (prototype/testing)**.
3. Enable that provider's chapter-sharing consent and check availability.
4. Select NVIDIA models in the desired Summary, Rewrite and Extract Terms route lists. Put them before Groq if
   larger chapters otherwise exceed Groq's quick whole-chapter allowance. Only selected, consented routes are tried.

The public live `/v1/models` catalog was inspected on 25 October 2026. The initial supported models are
`google/gemma-4-31b-it` and `nvidia/nemotron-3-super-120b-a12b`; both were listed. Although reference pages still
describe Llama 3.3 70B and `nvidia/nemotron-3-nano-30b-a3b`, those exact IDs were absent from that live list, so they
were not added. A listing is not proof of authenticated inference entitlement or capacity. Runtime checks filter the
live list to curated models; there is no invented availability fallback.

Requests use fixed HTTPS endpoints, existing encrypted credentials, non-streaming output, bounded deadlines and
the shared cancellation/cache/fallback mechanisms. Both models have reasoning disabled using their documented
options. Both send `chat_template_kwargs: {enable_thinking: false}`, the actual template control used in NVIDIA's
reasoning-off examples. Nemotron Super additionally sends `reasoning_effort: none`; sending that selector alone
does not mirror the examples, which translate it into template kwargs. Requests permit at most 8,192 output tokens (summary: 2,048).
These hosted endpoint references do not promise `response_format` support. Instead, the fixed output schema is
included in the instruction, with mandatory local structural, completeness, numerical and evidence validation.
This may produce more validation failures than constrained decoding; malformed output is rejected, never repaired
with fabricated chapter data. Model output quality still cannot be guaranteed by structural validation.

Summary segments are bounded to 64,000 input bytes, extraction to 32,000, rewrite to 16,000. Groq's 8,000-token
admission budget is not applied to NVIDIA. The existing conservative local six-generation-starts/minute/provider
limit is an application safeguard, not a claim about NVIDIA's allowance. Actual 429 responses and Retry-After
control provider-wide cooldown/fallback; timeout or capacity failures do not trigger unbounded retries. An HTTP 202
queued response is reported as unavailable and can fall back; this client deliberately does not poll asynchronous jobs.

No dependencies, Android permissions or database migrations were added. The initial integration had no NVIDIA key
and performed no authenticated generation. Subsequent user-authorized tests returned 404 for Super and timed out
for Gemma on all three actions; see [AI route verification](AI_ROUTE_VERIFICATION.md). Offline adapter/repository/settings tests cover setup, all three actions,
large-summary fallback, cache reuse, malformed/truncated/refused output, wrong-model responses and HTTP failures.

Initial integration verification on 25 October 2026 (before the summary-response follow-up): the full offline unit suite was executed freshly with test caching disabled:
285 tests, 282 passed, 3 skipped, no failures/errors. The isolated debug APK build succeeded with Gradle deprecation
warnings treated as failures. Outputs were isolated under `build/ai-nvidia-isolated` because a concurrent UI-agent
build made a shared generated classes JAR temporarily unreadable; the shared JAR was readable afterward. No
production Gradle configuration was changed for this isolation. The APK is at
`build/ai-nvidia-isolated/app/outputs/apk/debug/app-debug.apk`. These checks do not verify live inference, NVIDIA
account entitlement, response quality, phone lifecycle behavior or device temperature.

The subsequent summary-response follow-up added the explicit Nemotron template control, bounded JSON code-block
handling and specific sanitized validation errors. Its isolated `assembleDebug --offline --warning-mode fail`
build passed. The automated suite was not rerun for that follow-up and a failing authenticated device response
was not captured, so its exact original validation failure remains unconfirmed.

## Sources

- [NVIDIA NIM for Developers](https://developer.nvidia.com/nim)
- [NIM general FAQ](https://docs.api.nvidia.com/nim/docs/product)
- [NVIDIA staff guidance on free API rate limits](https://forums.developer.nvidia.com/t/api-rate-limit-increase-is-not-granted-by-requesting-it-here/368420/1)
- [NIM FAQ and account-specific rate limits](https://forums.developer.nvidia.com/t/nvidia-nim-faq/300317)
- [Hosted LLM API and model catalog](https://docs.api.nvidia.com/nim/reference/llm-apis)
- [Live hosted model list](https://integrate.api.nvidia.com/v1/models)
- [Gemma hosted request parameters](https://docs.api.nvidia.com/nim/reference/google-gemma-4-31b-it-infer)
- [Nemotron Super hosted request parameters](https://docs.api.nvidia.com/nim/reference/nvidia-nemotron-3-super-120b-a12b-infer)
