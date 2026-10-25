# AI Reading Tools: backend and UI contract

Implemented 24 October 2026. The backend, Settings page, Quick Settings actions/results, and reversible reader display
are integrated. No remote service is deployed, no keys are bundled, and no provider is contacted automatically on
launch or normal reading. Users configure their own keys, model routes, sharing consent and cost policy in Settings.

## Product behavior

For the verified action-to-route mapping, the Extract retry correction and actual NVIDIA HTTP results, see
[AI route verification](AI_ROUTE_VERIFICATION.md). Catalog availability alone does not establish generation access.

- Summary operates on the loaded **original** chapter and returns a concise summary, source-referenced key events,
  and chapter-local terms (characters, locations, abilities, items, organizations, concepts). No subsequent chapter
  is sent, including prefetched text. Evidence strings and term spellings must occur in their referenced paragraph.
- Rewrite means grammar/readability correction in the source language. It preserves paragraph boundaries and order,
  names, terminology, numbers, dialogue meaning and story facts in the prompt. Local checks enforce paragraph IDs,
  completeness and numerical sequences. They cannot prove semantic accuracy or guarantee that a name/fact was preserved.
- A complete validated rewrite becomes a **display version**, never an update to `Chapter.paragraphs`, a parser,
  source cache, bookmark, chapter URL, catalog or reading-history record. Users must be able to show the original.
- Summary and rewrite always use original text, even while a rewrite is displayed. Copy copies the displayed version.
- Narration still reloads original text through `NovelRepository`; an active narration session is unchanged. The UI
  must say that narration reads original text when displaying a rewrite. Narrating AI rewrites is outside this version.
- There is no novel-wide glossary, translation, creative rewrite, automatic regeneration, background generation,
  automatic continuation after interruption, or AI-generated chapter prefetch.

## Provider and cost boundary

### Summary validation diagnostics (25 October 2026)

A reported Summary failure used NVIDIA Nemotron 3 Super, followed by Groq GPT-OSS 120B/20B. The old message
collapsed JSON formatting, source-reference, field, category and exact-evidence failures into one generic
"incomplete or invalid chapter text" error. It therefore cannot establish which check rejected that device's response.
No failing live response or current credential was captured for this report.

The NVIDIA request now explicitly sends `chat_template_kwargs.enable_thinking=false`, alongside Super's
`reasoning_effort=none`, following the reasoning-off template example. Previously Super sent only the selector,
while NVIDIA's reference says its snippets translate that selector into template kwargs.

The validator accepts a UTF-8 BOM and a single enclosing JSON code block around a complete object. It still
rejects reasoning/prose surrounding JSON, extra objects, trailing text, truncated output, invalid evidence, invalid
references and incomplete rewrites. Nothing is sliced out of a response to fabricate a successful result.
Static action-specific errors now identify the rejected component, and failed routes include their curated model
label. No response text, source quotes, credentials or raw provider diagnostics are included in these errors.
Summary/merge instructions now state the event-reference bounds and tell the model to omit optional terms it cannot
support exactly. No new generation retry/repair call is introduced, and valid caches remain reusable.

These changes address confirmed request/response handling gaps. An authenticated reproduction is still needed to
determine whether this user's original rejection was caused by formatting, evidence, references or another field.

`AiProviderCatalog` is the only accepted model list. It currently contains Gemini Flash/Flash-Lite, Groq GPT-OSS/Qwen,
two OpenRouter Gemma free variants, and opt-in NVIDIA Gemma 4/Nemotron 3 Super prototype/testing routes.
Model IDs/capabilities are maintained centrally and availability is checked
against authenticated metadata. Native model listing does not establish free entitlement or remaining quota.
Gemini discovery follows at most four pages of 100 models; model discovery has a 45-second deadline.

OpenRouter's public catalog and endpoint metadata were checked on 24 October 2026. Both Gemma free models remain
listed with zero prompt/completion prices and published endpoints. Their exact IDs are
`google/gemma-4-31b-it:free` and `google/gemma-4-26b-a4b-it:free`. The previously shipped IDs omitted `-it`, causing
the availability check to report both as unavailable. Saved Summary/Rewrite routes with those two specific old
OpenRouter IDs normalize to the corrected IDs on read; order, sharing consent, cost policy and encrypted keys are
preserved. Normal settings saves subsequently persist the corrected selections. The read-time normalization does
not emit a configuration revision or cancel the first generation request. Other/paid IDs are not converted.

These free endpoints advertise `response_format` (JSON mode), without `structured_outputs` (strict schema mode).
Their requests use `json_object` and include the fixed response schema in the system instruction. Local structural,
completeness, numerical and evidence validation remains mandatory before any result reaches the reader/cache.
Groq and Gemini retain their schema-based requests. Discovery also checks the model's advertised output-mode
capability. Zero-price limits, explicit free IDs, consent and bounded fallback rules are unchanged.

Public checks (metadata only, no generation or user key access):

- https://openrouter.ai/api/v1/models
- https://openrouter.ai/api/v1/models/google/gemma-4-31b-it:free/endpoints
- https://openrouter.ai/api/v1/models/google/gemma-4-26b-a4b-it:free/endpoints

A reduced public-catalog snapshot under `app/src/test/resources/ai/openrouter-gemma-free-models.json` protects the
exact IDs and capabilities in offline tests; it is test data, never an application availability fallback. Published
endpoints do not establish remaining allowance, user-specific access or successful inference with the user's key.
The corrected build succeeded with Gradle deprecations treated as failures. The current full unit-test results cover
208 tests: 206 passed, 2 live-source checks skipped, no failures/errors. Regression coverage includes literal live
model IDs, saved-route migration/deduplication, free-price/capability checks, JSON-mode wire format and local rejection
of incomplete results; Groq strict-schema requests are also checked. No live inference request or user key was used.

The default `EXPLICIT_FREE_ONLY` policy permits only curated OpenRouter `:free` entries. Immediately before generation,
the cached metadata must list zero prompt/completion pricing (and zero request pricing when provided). Metadata expires
after five minutes and is keyed to the credential fingerprint. Requests specify zero maximum prompt/completion/request
pricing, a fixed free model, no hidden provider fallback, no paid model fallback array, and no tools/plugins. If these
constraints cannot be met, the request fails. We do not use the random `openrouter/free` router for rewriting.

Gemini/Groq/NVIDIA are opt-in through `FREE_ACCOUNT_PROVIDERS`. NVIDIA's hosted free access is for prototype/testing
only and is rate-limited; it is not free unrestricted production access. See [NVIDIA integration and setup](NVIDIA_AI_RESEARCH.md).
NVIDIA uses schema instructions plus mandatory local validation, not an assumed unsupported response-format parameter.
The UI must require acknowledgement before saving this policy:
the app cannot verify account billing status or prevent provider charges on a billed API key. Use a provider-enforced
free account/project if enabling native providers. This setting is an acknowledgement, not a billing cutoff. The app
never enables billing, upgrades an account, buys credits, or chooses an unlisted paid model.

Every provider also needs membership in `consentedProviders`. Enabling multiple fallback providers means chapter text
can be sent to those providers in the selected order. Explain provider data processing before consent. Gemini's free
tier currently describes content use for product improvement. Quotas and catalog availability can change.

Official references checked during implementation:

- https://ai.google.dev/gemini-api/docs/pricing
- https://ai.google.dev/gemini-api/docs/rate-limits
- https://ai.google.dev/api/generate-content
- https://console.groq.com/docs/rate-limits
- https://console.groq.com/docs/api-reference
- https://console.groq.com/docs/structured-outputs
- https://openrouter.ai/docs/guides/routing/model-variants/free
- https://openrouter.ai/docs/guides/routing/provider-selection
- https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties

## Ownership and execution

`NovelNestApplication` lazily owns `aiConfiguration`, `aiResultCache`, `aiModelDirectory`, and `chapterAiRepository`.
It owns a cheap `aiForeground` lifecycle signal. There is no Hilt/Koin layer, new service, Worker, or WorkManager job.

`ReaderViewModel` owns `ReaderAiController` in its existing `viewModelScope`. No composable owns generation, decrypted
credentials or an HTTP client. The repository has no independent generation scope. A non-waiting mutex admits at most
one application-wide action; the controller ignores duplicate taps. When replacing a cancelled reader operation, it
waits for the old job to release repository admission before starting the replacement. There is no unbounded work queue.

Each request snapshots source/novel/chapter identity, original paragraphs and title. Cache identity adds a length-framed
SHA-256 source fingerprint, action and prompt/schema version. A controller generation token prevents old progress/results
from appearing after cancellation or chapter changes. Source refresh invalidates both resident results and the display
version. Completed results contain actual provider/model provenance. Cache reuse is not forced regeneration when a
different provider is selected later; the UI should identify the provider that produced the cached result.

Hashing, JSON, chunk planning and validation run on Default; preferences, Keystore and disk run on IO. OkHttp uses
asynchronous calls and bounded reads. Cancellation stays registered until the response body has been read and closed;
TLS/socket cancellation is dispatched to IO, never executed directly by a main-thread cancellation callback. The
application-lifetime AI client is isolated from scraper and narration clients and is not torn down when a sheet closes.

## Bounds and fallback

- Loaded source limit: 96,000 UTF-8 bytes, 2,000 paragraphs, title at most 1,000 characters.
- At most three explicitly selected routes per action, eight paragraph-aligned segments per attempt, twelve generation
  POSTs across the entire operation including failed attempts and summary merging, and five minutes overall.
- Rewrite segment source budget is 16,000 UTF-8 bytes for Gemini/OpenRouter and 2,000 for Groq.
  Summaries have independent limits: Gemini/OpenRouter use up to 64,000 UTF-8 bytes per segment; Groq plans the
  complete chapter as one request against the 8,000-token local budget, including prompt/schema/framing and a
  2,048-token completion reservation. Oversized Groq summaries try the next enabled route before sending any
  chapter text; they do not spend on partial summaries that need minute-long quota waits.
  Extraction also has its own planner: Gemini/OpenRouter allow 32,000 UTF-8 bytes per segment; Groq
  preflights a single whole-chapter request including the topic and its full 3,072-token output reservation.
  Oversized Groq extraction tries the next enabled **extraction** route before sending text. It does not
  lower the output budget to squeeze in more input. Segment results are combined locally without a merge call.
  JSON envelope size and expected output are additionally bounded. A paragraph is never silently split or
  omitted; if it cannot fit, another eligible larger-capacity route is tried or an actionable size error is returned.
- Typical chapters use one call. Long summaries use segment summaries plus one bounded merge. Long rewrites use one
  model throughout an attempt. Fallback restarts from original text; partial output is never applied or cached.
- Local admission permits at most six generation starts/minute per provider budget; Groq budgets are scoped to each
  curated model, while other providers retain shared local budgets. Credentials are not rotated to evade quotas.
  Groq also estimates input/output usage
  against 8,000 tokens/minute, counting padded ASCII prose at three bytes/token and non-ASCII bytes at one byte/token,
  including prompt/schema/framing and the full possible completion. This is an estimate, not an exact tokenizer or
  account quota counter. Each successful provider response settles its reservation to reported total token usage,
  including reasoning, even if subsequent local output validation fails. Missing usage and ambiguous cancelled
  requests retain their reservation. No refund is inferred from output character count.
- Groq response headers supply remaining TPM tokens and DAILY requests, with independent reset durations. Fresh
  server counters replace already-accounted local estimates; subsequent unknown requests retain separate reservations.
  Daily reservations survive expiry of the minute window. No chapter text, keys or organization IDs are retained in
  quota state. Account/project restrictions and separate input/output limits remain provider-enforced.
- Summary and extraction admission wait at most ten seconds for a local slot, then try another enabled provider or
  report a retry delay. Rewrite retains its existing one-minute admission wait. Waiting suspends without polling and
  displays an approximate reset delay; it is included in the overall deadline. The display is not a live countdown.
- Real 429/5xx responses establish a remote cooldown (retry/reset hints honored up to seven days; defaults 60/30 seconds
  only when no hint is available). Local admission/cooldown checks never renew remote expiry. Known Groq model quota
  failures may try the next configured model; unknown/shared limits and 5xx retain provider-wide blocking. Daily token
  and daily request failures are reported separately from per-minute failures. One minute is not a promised reset time.
  These counters are process-local. Other applications/devices and account/project quotas remain provider-controlled.
- Groq's currently documented free allowance is 8K TPM for the curated models; the account limits page remains
  authoritative: https://console.groq.com/docs/rate-limits . Large model context windows do not increase free TPM.
  Genuine provider throttling/capacity and network latency cannot be eliminated by client scheduling. No billing
  policy, consent, output validation, source coverage or durable notes/terms storage is relaxed by summary planning.
- Offline/DNS/connection failures and explicit refusals stop the action. Invalid credentials, rate limits, unavailable
  providers, unavailable models, timeouts and invalid/truncated results may advance to the next enabled eligible route.
  Credential/shared-quota/provider failures skip remaining models under that same provider for this action;
  a known model-scoped Groq quota only skips that model.
- No same-request automatic retry, repair-prompt loop, hedged parallel generation, key rotation to evade quotas, or
  fallback to a provider without consent. HTTP errors and partial output never become replacement chapter text.

HTTP requests are HTTPS only, fixed-endpoint, redirects disabled, automatic connection retries disabled, no disk HTTP
cache, and no chapter/key/body logging. Generation responses are limited to 512 KiB, metadata to 4 MiB, and parsed
chapter result JSON to 256 KiB. Provider messages are not surfaced verbatim. Explicit safety/refusal signals stop fallback.

## Credential and result storage

`AiConfigurationStore` uses its own Android Keystore AES-GCM alias and `ai_reading_tools` preference file. The entire
preference file is excluded from cloud backup/device transfer. A restore/new device requires key setup. Unreadable keys
are reported, never treated as valid configuration. Blank replacement keeps the saved key; remove-key is explicit.
No decrypted key appears in a StateFlow or SavedStateHandle. Provider keys are sent only to their fixed provider host.

Settings route choices and keys are saved by separate explicit methods; a multi-field form must not claim an atomic
save across those methods. Wait for each save to complete, clear transient entered key text after success, and report
individual failures. Saving configuration increments a revision that cancels pending reader generation.

`AiResultCache` is a disposable private cache (`cacheDir/ai_results`), at most 16 MiB/96 disk entries, 384 KiB per stored
envelope and four bounded resident results. Atomic file replacement and one owner mutex protect read/write/clear.
Clear increments an epoch so an older in-flight generation cannot repopulate the disk cache. Cache failure does not
discard an otherwise valid generation. Android can delete disposable files. Already displayed results remain in reader
memory until source/navigation changes; clearing cache does not delete reader data or credentials.

There is no Room schema migration, new runtime permission, dependency, backend deployment or billing configuration.

## Lifecycle and reading compatibility

| Event | Behavior |
|---|---|
| Rotation | Existing ViewModel/job retained; Activity configuration stop does not mark app background |
| Dismiss Quick Settings | Same-reader generation continues; reopening observes the same state |
| Select next/previous/another chapter | Cancel and invalidate old request; source text drives the next request |
| Reader destination disposed | Cancel, except configuration recreation |
| Lock screen, Home, another application | MainActivity stop signals background and cancels; no auto-resume |
| Key/preferences saved or removed | Cancel pending generation; later explicit action uses new configuration |
| Process killed / removed from Recents | No generation restart; completed cache can be used on another explicit action |
| Launcher reopened | Existing Home-first behavior and selected source unchanged |
| AI fails | Original stays readable, no bookmark/progress rewrite, typed error exposed to the UI |
| Audio currently playing | Original-text narration continues through its own service |

The provider may still finish work already accepted after client cancellation and consume allowance. OEM process
termination cannot be prevented. No claim of zero heat is made: inference is remote, but the screen/radio/JSON work
still consume power.

`ReaderScreen` already uses `ReaderAiState.paragraphs(chapter)` for display, versioned lazy row identity, separate small
scroll checkpoints and displayed-version Copy. First switch to rewrite maps to the same paragraph at a safe offset;
returning to original restores its own checkpoint. Fast scroller, navigation, metadata and read counts retain original
chapter identity. No additional mark-read call is made by applying a rewrite.

## UI integration contract

### Settings page

Name the page **AI Reading Tools**. Add a `SettingsSubPage.AI_READING_TOOLS` case and a tile in `SettingsMainView`.
Create the page/ViewModel with the existing factory pattern:

```kotlin
AiReadingToolsViewModel(app.aiConfiguration, app.aiModelDirectory, app.aiResultCache)
```

Collect `state` with lifecycle awareness; `supportedModels` is the curated selection list.

| Method | Meaning |
|---|---|
| `reload()` | Load settings and nonsecret key status; no network/inference |
| `saveSettings(AiSettings)` | Save per-action ordered route lists (max three), consent, cost policy |
| `replaceKey(provider, replacement)` | Blank keeps key; nonblank encrypts replacement; clear entered text on success |
| `removeKey(provider)` | Explicitly remove key and invalidate model metadata |
| `checkProvider(provider)` | Authenticated model-list GET; no generation; updates per-model availability |
| `clearResults()` | Clear only AI disposable result files/memory cache |
| `dismissMessage()` | Clear transient outcome/error |

`state.providers` reports `configured` and `unreadable`. Never obtain decrypted keys for form prefilling. `busy` disables
overlapping settings commands. `availableModels` reflects the last explicit check, not a live quota or billing promise.
Settings saves and metadata checks are distinct; never label metadata success as generation verified. On unreadable
settings, allow saving new defaults and replacing/removing keys instead of trapping the user in a disabled form.

All modifying controls, keyboard submission and confirmation actions respect `busy`. `savedKeyRevisions` acknowledges
successful persistence per provider without publishing credentials; entered key text clears only when its provider's
revision advances. A failed save preserves the input. The form registers each key field with `FormFocusController` so
outside taps dismiss focus/keyboard while taps within fields keep editing. Native-provider policy selection requires
an explicit billing acknowledgement dialog before saving; cancellation leaves the previous policy unchanged.

### Reader Quick Settings

Reader error presentation preserves sanitized backend messages, including the specific size/quota explanation and
retry delay. Empty-message fallbacks use the action that failed; Summary and Extract never inherit a rewrite warning.

Observe `ReaderViewModel.aiState` and pass state/callbacks into presentational components:

| Callback | Meaning |
|---|---|
| `requestAiSummary()` | Generate or reuse summary/terms for the loaded original chapter |
| `requestAiRewrite()` | Generate/reuse validated rewrite and switch displayed version |
| `cancelAiRequest()` | Cancel pending work; preserve completed resident results |
| `showAiContentVersion(ORIGINAL/REWRITE)` | Reversible display switch; rewrite requires a completed result |

`ReaderAiState` exposes `chapterId`, `sourceTitle`, `sourceParagraphs`, `action`, `progress`, `failure`, `summary`,
`rewrite`, `version`, `busy`, and `contentKey`. Result summary is `summary?.summary`; rewrite content is
`rewrite?.rewrittenParagraphs`. Results include route, fingerprint and creation time. `AiFailure` has a typed code,
safe message and optional retry delay. Only show chapter results when `aiState.matches(currentChapter)`.

Progress phases: IDLE, CHECKING_CACHE, PREPARING, WAITING_FOR_SLOT, CONNECTING, GENERATING, SWITCHING_PROVIDER,
VALIDATING, READY, FAILED, CANCELLED. Provider/chunk/totalChunks/message are available. A GENERATING phase means
the request body was sent and the response is awaited, not that the model has reported a percentage.

Disable generation for unavailable/loading source text and while `busy`. Cancel remains enabled. Explicit Retry
invokes the same action after failure, subject to cooldown. Opening/closing/composing the sheet must never trigger
generation. Do not replace the whole `ReaderUiState` with AI text or reimplement reader display/scroll logic.

The rewrite banner is inside the existing title list item, keeping paragraph indexes stable when it appears. AI cards
use the reader's theme surface/text pairing, including Paper and Parchment. Result display requires the complete
source match, not just the chapter ID. Availability labels never claim that model listing verified generation.

## Original UI handoff prompt (reference)

```text
Implement only the UI for the already implemented AI Reading Tools backend. Read docs/AI_READING_TOOLS.md and
the actual AiReadingToolsViewModel, ReaderAiState, ReaderViewModel APIs before editing. Do not invent a second
provider client, request scope, fallback mechanism, credential store, content cache or rewritten Chapter object.

Preserve the existing NovelNest design, reader themes, typography, narration controls, navigation, bookmarks,
source tabs, launch behavior, footer and mini-player spacing. Reuse the existing settings tile/header components,
FormFocusController and reduced-motion helpers. No new global animation or polling loop.

Add Settings > AI Reading Tools with an internal SettingsSubPage entry. Use the existing ViewModel factory pattern
and AiReadingToolsViewModel(app.aiConfiguration, app.aiModelDirectory, app.aiResultCache). Use lifecycle-aware
state collection. Provide provider cards, masked blank key replacement inputs, explicit save/remove/check actions,
model selection from supportedModels, ordered Summary and Rewrite routes, provider-sharing consent, cost-policy
selection, clear-result-cache and actionable error/status states. Respect busy. Keep keys out of rememberSaveable,
SavedStateHandle, previews and logs. Do not load decrypted keys into UI state. Clear an entered key after its save.
Metadata Check is model availability only: never label it generation verified or allowance/billing verified.

Default to EXPLICIT_FREE_ONLY. FREE_ACCOUNT_PROVIDERS must require explicit acknowledgement that Gemini/Groq
keys may incur provider charges if attached to a paid account. Do not promise unlimited usage or an app-enforced
billing cutoff. Explain that all consented fallback providers can receive the chapter. Save methods are separate,
not one atomic settings-plus-keys transaction. Provide recovery when stored settings/keys are unreadable.

Add Summary and Rewrite to ReaderQuickSettingsSheet. Consume ReaderViewModel.aiState and call only
requestAiSummary(), requestAiRewrite(), cancelAiRequest(), showAiContentVersion(). The reader already displays
rewrites and handles versioned scroll/Copy; do not duplicate this implementation. Pass state and callbacks down.
Show Summary, key events and important terms inside Quick Settings, with bounded/lazy result scrolling. Show safe
typed errors, provider/model provenance, cancellation and explicit Retry. Use actual progress phases/chunk counts,
not a fabricated percentage. Disable duplicate generation while busy and when source text is unavailable/loading.
Only show results matching the current chapter. Keep Cancel available while waiting/loading.

Add a clear AI rewrite indicator and Show original/Show rewrite switch. Explain that narration reads original text.
Preserve an existing narration session. Never generate in composition, effect-on-open, navigation or source prefetch.
Do not modify source parsers, repository chapters, reading progress, narration service, credentials or fallback logic.
Use fake state for previews only. Exercise loading, no configuration, refusal, quota, cancellation, long results,
large fonts, reduced motion, mini-player overlap, original/rewrite switching and offline cached results.
```

## Verification boundaries

Offline tests cover provider wire formats using interceptors (no sockets), zero-price constraints, refusal and truncated
output, fallback/cancellation/duplicates, long-chapter assembly/merge, changed-source protection, cache corruption/clear,
and controller chapter/version races. Existing ReaderStabilityTest is included in focused verification.

UI integration baseline: **60 tests passed** (54 AI/settings tests and 6 existing reader stability tests), and
`:app:assembleDebug` succeeded. New settings tests cover delayed/failed key persistence, provider-specific save
acknowledgements, overlapping submissions and settings/consent saves. No new dependency was downloaded or added.
The existing unrelated chapter-sort working-tree change was preserved.

No live API key or inference request was used. Before release, validate the selected free entries and account-specific
native model access, actual structured output, rate-limit resets and long-chapter quality against real providers. Test
Keystore restore/key invalidation, outside-tap keyboard dismissal, all reader themes, original/rewrite scroll switching,
and Activity rotation/background behavior on a phone. Measure
scrolling performance and temperature; compilation/offline fakes cannot establish those results.
