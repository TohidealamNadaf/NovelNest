# Live provider verification — 2026-10-24

These were real authenticated HTTPS calls through `ChapterAiRepository`, `ChatAiProvider`, `AiHttpClient`, the
production prompts/schemas, request admission and local response validator. The input was two original test-story
paragraphs containing characters, cultivation concepts, an ability, an organization, items and numerals. It was not
a full novel chapter or a reading-quality evaluation. No credentials, chapter text or generated text are in this report.

## Results

| Provider/model | Observed result |
| --- | --- |
| OpenRouter `google/gemma-4-31b-it:free` | Authenticated metadata eligible under zero-price/output checks. First Summary POST returned HTTP 429. Generation could not be validated. |
| OpenRouter `google/gemma-4-26b-a4b-it:free` | Authenticated metadata eligible. Generation was not sent because the first OpenRouter failure activated the provider cooldown. |
| Groq `openai/gpt-oss-120b` | Initial extraction passed, initial Summary/Rewrite failed local validation despite HTTP 200. A diagnostic repeat passed both with `strict: true`. After prompt/temperature changes all three actions passed in one run: Summary, two rewrite paragraphs and four extracted terms. |
| Groq `openai/gpt-oss-20b` | Initial Summary/Rewrite passed; initial extraction failed local validation despite HTTP 200. Not retested after the shared fidelity changes. |
| Groq `qwen/qwen3.8-27b` | Initial Rewrite passed; initial Summary/Extract failed local validation despite HTTP 200. Not retested after the shared fidelity changes. |

There were exactly 15 generation POSTs across the three runs: 1 OpenRouter and 14 Groq. Metadata GETs were additional.
OpenRouter's remaining action rows in the first report are local cooldown rejections, not additional generation requests.
The first live test intentionally failed when any configured action failed; this is a diagnostic result, not an offline-test regression.

The original reported Groq HTTP 400 `json_validate_failed` was **not reproduced**. Its message identifies a server-side
enum/schema failure. The current app sends `strict: true`; a successful schema does not guarantee verbatim evidence,
correct references, preserved numerals or semantic fidelity. Initial HTTP-200 rejections show why the additional local
checks must remain. Because generated text was not retained in the initial run, the exact rejected field in those initial
responses cannot be established retrospectively. The diagnostic repeat passed without changing those checks.

OpenRouter's catalog eligibility verifies advertised zero pricing, not successful inference. The returned 429 establishes
rate limiting; this test did not establish whether it was account quota or free-endpoint capacity, or its reset time. No
successful OpenRouter inference response/cost receipt was obtained. Groq Free-plan status was confirmed by the account
owner, not by a billing API. Groq responses did not expose a monetary cost field. API success alone does not prove a plan
is free, and the app does not change account subscriptions or bypass allowances.

## Changes

- Recognize Groq's `json_validate_failed` as a specific, safe INVALID_OUTPUT message; retain configured fallback behavior.
  Raw provider messages and `failed_generation` are not exposed or saved.
- Clarify allowed term kinds and map realms/stages/energy to CONCEPT, techniques to ABILITY and sects to ORGANIZATION.
- Reinforce exact-case, verbatim evidence and original paragraph IDs, plus exact numeral spelling/order for rewriting.
- Use Groq temperature 0.2 for factual reading operations. Strict schemas, evidence checks, numeral checks, request/call
  budgets, account consent, free-only routing and no-partial-output behavior remain enforced.
- Add offline regressions for safe schema-error classification and strict output on every curated Groq model/action.
- Add an explicitly opted-in live test. Live tests cannot reuse Gradle up-to-date/build-cache results. They are skipped
  in normal unit runs, and no live credentials are embedded in the APK or test sources.

Previously validated cached results remain usable: the output contract is unchanged and rejected results were never cached.
The prompt changes reduce common fidelity errors; they do not prove that future model outputs will always be correct.

## Repeating the live test

Provision credentials only through temporary process environment variables `OPENROUTER_API_KEY` and/or `GROQ_API_KEY`.
Do not put real values in source files, Gradle properties, scripts, command-line Gradle properties or reports. Opt in with
`AI_LIVE_SMOKE=1`. Groq additionally requires `GROQ_FREE_PLAN_CONFIRMED=1` after checking the account tier.

Optional `AI_LIVE_CASES` selects pipe-separated `model:ACTION` cases, for example
`openai/gpt-oss-120b:SUMMARY|openai/gpt-oss-120b:REWRITE|openai/gpt-oss-120b:EXTRACT`.
`AI_LIVE_MAX_REQUESTS` caps generation POSTs per run (1–15, default 15). Requests are not retried outside the production
pipeline. The conservative free-plan limiter can space operations by a minute.

Run `gradlew.bat :app:testDebugUnitTest --tests com.novelnest.app.data.ai.AiLiveProviderTest --offline --warning-mode fail`.
Gradle `--offline` affects dependency resolution, not this explicitly opted-in HTTPS test. Sanitized results go to
`app/build/reports/ai-live/report.json`. Remove all live-test flags and credential variables after testing. Rotate credentials
that were shared in a conversation. Do not publish a shared app-wide key.

Primary documentation: [Groq structured outputs](https://console.groq.com/docs/structured-outputs),
[Groq billing tiers](https://console.groq.com/docs/billing-faqs), [OpenRouter model catalog](https://openrouter.ai/api/v1/models).

Final verification: GPT-OSS 120B live Summary/Rewrite/Extract all validated in the final three-request run. The
subsequent `assembleDebug :app:testDebugUnitTest --offline --warning-mode fail --max-workers=1` succeeded with
live flags/credentials absent: 227 tests total, 224 passed, 3 explicitly skipped, zero failures/errors. The APK is
`app/build/outputs/apk/debug/app-debug.apk`. No new Android device or full-length real-chapter test was performed.
