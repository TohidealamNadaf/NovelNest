# OpenRouter free chapter models — 2026-10-26

## Catalog verification

Verified against the public [OpenRouter models API](https://openrouter.ai/api/v1/models).
All 16 requested IDs were present with zero prompt and completion prices. The app
offers 13 chapter-generation candidates (11 additions and the two existing Gemma
models). Nemotron 3.5 Content Safety is a moderation classifier. Both Inkling free
endpoints restrict access to agentic harnesses and rejected this reader's requests;
all three are deliberately excluded from Summary, Rewrite and Extract.
See the [Inkling](https://openrouter.ai/thinkingmachines/inkling:free) and
[Inkling Small](https://openrouter.ai/thinkingmachines/inkling-small:free) endpoint notices.
This is a snapshot, not a promise of continued availability or unlimited requests.

| Model | Exact OpenRouter ID | Output protocol |
| --- | --- | --- |
| Gemma 4 31B | `google/gemma-4-31b-it:free` | JSON object |
| Gemma 4 26B A4B | `google/gemma-4-26b-a4b-it:free` | JSON object |
| Flint | `recursive/flint-v1.0:free` | JSON object; mandatory reasoning |
| Xing4.0 29B A4B | `xingchen-agi/xing4.0-29b-a4b:free` | JSON schema |
| Nemotron 3.5 Super VL | `nvidia/nemotron-3.5-super-vl:free` | JSON schema |
| Dots3-Note Preview | `dots-studio/dots-3-note-preview:free` | JSON schema |
| LFM2.5-2.6B | `liquid/lfm-2.5-2.6b:free` | JSON schema; mandatory reasoning |
| Nemotron 3.5 Lightning | `nvidia/nemotron-3.5-lightning:free` | Prompt JSON |
| Laguna S 2.1 | `poolside/laguna-s-2.1:free` | Prompt JSON |
| Laguna XS 2.1 | `poolside/laguna-xs-2.1:free` | Prompt JSON |
| North Mini Code | `cohere/north-mini-code:free` | Prompt JSON |
| Nemotron 3 Ultra | `nvidia/nemotron-3-ultra-550b-a55b:free` | Prompt JSON |
| Nemotron 3 Nano Omni | `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free` | Prompt JSON |

The catalog reports **2026-10-31** expiry for both Laguna variants and **2026-12-31**
for Dots3-Note Preview. Their selector labels include those dates. Availability is
checked again through the existing metadata cache (five-minute lifetime); if a
model disappears or its prices change, the app can only try the user's next
configured route. Saved routes and their priority are not automatically changed.

Flint, Laguna and North Mini Code focus on coding. They are selectable candidates,
not recommendations of proven fiction-summary quality. Large context windows and
multimodal support do not justify sending larger chapters or images; the app's
existing text-only input limits remain in force.

## Adapter changes and safeguards

- Use advertised JSON schema / JSON object support where available. Models without
  response-format support receive the fixed schema in the prompt. All responses
  still pass the same local structural, citation, evidence and rewrite validators.
- Do not send an unsupported reasoning-disable instruction to Flint or LFM.
  Their summary completion ceiling is 8,192 tokens including reasoning, leaving
  space for final JSON. Other summaries keep their 2,048-token ceiling. Reasoning
  is excluded from returned messages and never displayed as chapter output.
  LFM's smaller context also uses a 32,000-byte summary input segment ceiling.
  See [OpenRouter reasoning controls](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens).
- Every OpenRouter request retains an explicit `:free` ID, zero maximum prompt,
  completion and request prices, required-parameter matching, and disabled hidden
  provider fallbacks. No paid alias or automatic model router is introduced.
- At most three user-selected routes per action; no automatic sweep of the new
  catalog. No startup inference, extra background work or model downloads.
- Existing cancellation, request caps, response size bounds, caches and provider
  cooldowns remain active. No database migration or permission change is needed.

Choose models under **Settings → AI Reading Tools → Summary / Rewrite / Extract
Terms Routes → Add Route**. Both cost-policy choices allow these explicitly free
OpenRouter IDs. Provider consent and a valid OpenRouter key are still required.

## Verification

Public metadata fixture: `app/src/test/resources/ai/openrouter-free-models-2026-10-26.json`.
Offline tests exercise capability matching, zero-price enforcement, mandatory
reasoning, rejection of invalid output and exclusion of the moderation classifier.
The opt-in live harness uses two original story paragraphs and the production
repository, provider adapter and validators. This checks integration; it does not
establish quality on every chapter or guarantee future uptime.

### Authenticated generation results

The supplied key authenticated as a free-tier account. There were **32 generation
POSTs** across two runs, plus three metadata GETs. The first run evaluated 45
model/action cases: 29 reached the network and 16 were rejected locally by provider
cooldowns. The second run sent one Summary request for each of the three models
that had not reached generation. No failed action was automatically retried.

| Model | Summary | Rewrite | Cultivation-level extraction |
| --- | --- | --- | --- |
| Gemma 4 31B | HTTP 429 | Cooldown; not sent | Cooldown; not sent |
| Gemma 4 26B A4B | HTTP 429 on direct check | Cooldown; not sent | Cooldown; not sent |
| Flint | Validated, 48.4 s | Validated | Validated, 2 records |
| Xing4.0 | HTTP 429 | Cooldown; not sent | Cooldown; not sent |
| Nemotron 3.5 Super VL | Validated, 5.0 s | Not verified | Not verified |
| Dots3-Note | Validated, 5.5 s | Validated | Validated, 2 records |
| LFM2.5-2.6B | Validated, 5.9 s | Validated | HTTP 429, Retry-After 1 s |
| Nemotron 3.5 Lightning | Validated, 6.8 s | Validated | Invalid model output; rejected |
| Laguna S 2.1 | Validated, 20.9 s | Validated | Validated, 2 records |
| Laguna XS 2.1 | HTTP 429 | Cooldown; not sent | Cooldown; not sent |
| North Mini Code | Validated, 2.0 s | Not verified | Not verified |
| Nemotron 3 Ultra | Validated, 7.2 s | Validated | Validated, 1 record |
| Nemotron 3 Nano Omni | Validated, 12.3 s | HTTP 200 with provider error code 502 | Cooldown; not sent |

Both Inkling variants returned HTTP 403 for all three actions during the initial
candidate sweep. Their documented harness restriction was then confirmed and
they were removed from the shipping catalog. Content Safety was inspected through
public metadata/documentation; no generation allowance was spent on the classifier.

There were **19 validated action results**. All 20 responses exposing a cost field
(including Lightning's rejected extraction) reported **0**. The final account
snapshot reported daily monetary usage **0**, free requests used **18/50**, remaining
**32**. Account quota counters are provider-defined and can lag; they are not the
same as the harness's count of HTTP attempts. No credits or paid tier were enabled.

Lightning's extraction response included a schema annotation as an output field
and malformed term records. The app rejected it before caching or publishing.
Nano Omni's rewrite returned an error object despite HTTP 200, which was also
rejected. Neither behavior establishes a defect in every future response from
those models; neither response was accepted as usable chapter content.

Both live test runs intentionally ended with a failed assertion because some
cases failed. Those failures are the recorded 429/403/provider/output results,
not a successful live suite. Offline regression checks and the final APK build are
reported separately below. Sanitized detailed reports are retained locally at
`build/openrouter-audit/initial-report.json` and `additional-report.json`.

For an initial user-selected route, Dots3-Note or Nemotron Ultra are reasonable
candidates based on all-action protocol success in this small fixture. Dots expires
on the advertised date. Flint also passed but its mandatory reasoning made Summary
substantially slower. North Mini Code's fast Summary alone does not establish its
rewrite or extraction quality. No route was selected automatically for the user.

"Validated" means schema, source-reference/evidence and rewrite integrity checks
passed. It does **not** prove all relevant terms were found or that every summary
is semantically faithful. In particular Ultra returned only one extraction record
where several other models returned two. Latencies are single observations on two
short English paragraphs, not a sustained benchmark or a full chapter guarantee.

### Offline/build checks

Final isolated verification completed successfully with `assembleDebug` and the
AI, reader AI controller and study merge unit tests: **14 suites, 159 tests,
158 passed, 1 opt-in live test skipped, 0 failures/errors**. Gradle ran offline
with `--warning-mode fail --max-workers=1`. `git diff --check` also passed.

APK: `build/ai-nvidia-isolated/app/outputs/apk/debug/app-debug.apk`.
These checks verify compilation and the exercised behavior; no phone UI or
sustained chapter-quality benchmark was run. No credentials were stored in the
project, APK or reports. Revoke the key shared in chat after verification.

Free pricing does not remove [OpenRouter account and capacity limits](https://openrouter.ai/docs/api_reference/limits).
Inference errors must be distinguished from metadata availability. Repeated
requests, fresh API keys and switching free models do not create a new account quota.
