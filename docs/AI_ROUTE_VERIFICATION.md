# AI route verification - 25 October 2026

## Result

New generations use the separately saved Summary, Rewrite or Extract route list, in order. The route order
reported by the user was NVIDIA Nemotron 3 Super, Groq GPT-OSS 120B, then Groq GPT-OSS 20B for all three actions.
No route preferences or credentials on the device were changed by this verification.

## Traced code path

1. `AiReadingToolsPage` changes only `summaryRoutes`, `rewriteRoutes` or `extractRoutes` for the corresponding card.
2. `AiConfigurationStore` serializes each ordered list separately, without sorting. Provider keys are separately
   encrypted; the generation repository retrieves the credential for `route.provider`.
3. Reader buttons pass the corresponding `AiAction` through `ReaderViewModel` and `ReaderAiController`.
4. `ChapterAiRepository` loads `settings.routes(action)`, iterates that list and stops at the first complete,
   validated result. Only eligible, consented routes with credentials are attempted. Invalid output can fall back;
   account-wide failures can skip remaining routes for that provider. Refusal, network and storage failures stop
   according to the existing policy. No unselected provider is added automatically.
5. A validated cache hit or already displayed result is reused without an inference request. Changing routes
   affects the next uncached generation; it does not regenerate or relabel an existing result. Cache keys include
   action, original chapter fingerprint and extraction topic, so these actions do not reuse each other's outputs.

## Confirmed retry defect and fix

The shared failure card previously selected Rewrite when the failed action was Rewrite, and Summary for every
other action. Therefore retrying an Extract failure there invoked Summary and its routes. It now dispatches all
three actions explicitly. Extract retries the original saved collection, checks that it still exists for the
current reader state and opens the Extract list. If that collection is gone or the chapter is not ready, it does
not start a Summary request. An absent failed action starts no request.

This changes callback wiring, without changing the card layout or the user's route order.

## Live NVIDIA results

The supplied key was used only through hidden process input and a temporary process environment, never source,
Gradle arguments, preferences or a credential file. The single-use Gradle/test processes exit afterward. The key
was shared in chat and should be revoked/replaced.

Tests call the production repository, provider adapter, request policy and validator with two original test-story
paragraphs. They do not use the user's phone preferences, current novel or another provider's API key.

| Model | Summary | Rewrite | Extract |
|---|---|---|---|
| `nvidia/nemotron-3-super-120b-a12b` | HTTP 404 | HTTP 404 | HTTP 404 |
| `google/gemma-4-31b-it` | Timeout, about 75 seconds | Timeout, about 75 seconds | Timeout, about 75 seconds |

The first metadata attempt failed with a network error and sent no generation request. Subsequent public metadata
checks succeeded via PowerShell, Java HTTPS and OkHttp HTTP/2 and HTTP/1.1. The Super batch then sent exactly three
generation requests naming the selected model, all returning 404 despite its catalog listing. A separate, bounded
three-request batch tested the already-curated Gemma alternative; no result completed within the existing timeout.
No timeout was increased and no failed output was accepted to make these checks pass.

A 404 does not prove whether the cause is per-account model access or the provider's deployment. Similar
catalog-200/inference-404 cases are reported in NVIDIA's official forum; account entitlement is a possibility,
not a confirmed diagnosis of this account. The app now states that NVIDIA catalog presence does not establish
generation access. These current HTTP failures do not identify the exact field rejected in the user's earlier
generic Summary validation failure, which could also have occurred on a fallback route.

The live assertions intentionally failed and are not counted as successful inference. Groq fallback order was
verified offline; this run did not use a Groq key or claim a successful live Groq generation.

Sanitized local evidence:

- `app/build/reports/ai-live/nvidia-super-report.json`
- `app/build/reports/ai-live/nvidia-gemma-report.json`
- `build/ai-nvidia-routes-verification.log`
- `build/ai-nvidia-gemma-live-check.log`

## Regression coverage

`AiRouteSelectionTest` covers independent action lists, the user's exact three-route order after invalid output,
stopping after primary success, reordered preferences on new chapters and reuse of validated cached results.
`AiFailurePresentationTest` covers explicit retry dispatch and the missing-action case. Existing response/wire
tests additionally cover complete JSON wrappers, rejection of reasoning/prose/truncation/trailing objects,
unchanged exact-evidence checks, safe diagnostics and explicit NVIDIA thinking controls.

The final offline suite and APK build use isolated generated outputs, with all provider keys and live flags
removed. Live failures remain documented separately; offline success is not evidence of NVIDIA service access.
Final result: **294 tests, 291 passed, 3 skipped, no failures/errors**. `assembleDebug --offline --warning-mode fail`
also succeeded. The verified APK is `build/ai-nvidia-isolated/app/outputs/apk/debug/app-debug.apk`.

## Provider reference

- [NVIDIA report of catalog success and inference failures](https://forums.developer.nvidia.com/t/post-v1-chat-completions-returns-500-404-for-all-tested-models-while-get-v1-models-returns-200/383194)
- [NVIDIA hosted access FAQ](https://docs.api.nvidia.com/nim/docs/product)
