# Unicode Escape Decoder

## Controls and scope

The preference is off by default and shared by:

- Settings -> Reading Behavior -> Unicode Escape Decoder.
- Novel reader -> Quick Settings -> Size & Spacing -> Unicode Escape Decoder.

Changing either switch updates the other. Resetting reading settings turns it off. Only NovelPhoenix and FreeWebNovel are allowed through the decoder. Asura and local EPUB paths do not use this transformation.

This option fixes residual visible escapes in plain chapter text, such as \u2019, &#x2019;, and &#8217;. Normal HTML entities are already handled when the website parsers extract text. The decoder does not parse HTML again, reinterpret markup, repair mojibake, or normalize ordinary Unicode characters.

## Data flow

Repository/cache Chapter (unchanged) -> reader-owned background preparation -> display, Copy Chapter, and AI source input.

A new narration session applies the same pure decoder before building its passages and text fingerprint. An already running session keeps its existing text. Stop it and start a new Listen session to apply a changed preference to that chapter. The feature does not synthesize speech or make network requests on its own.

Chapter IDs, source routing, URLs, paragraph order/count, read markers, bookmarks, notes, and existing extracted discoveries remain intact. Disabling the option immediately exposes the original repository text. AI source fingerprints follow effective title/body text, preventing reuse against a different representation. Existing AI source-change handling cancels stale work; toggling never starts generation automatically.

## Decoding rules

- One pass, one layer: replacement text is not scanned recursively.
- JavaScript-style lowercase \u followed by four ASCII hexadecimal digits, including valid adjacent UTF-16 surrogate pairs.
- Decimal and hexadecimal numeric entities require a terminating semicolon and a valid Unicode scalar.
- Literal paired backslashes remain literal. Generic backslash escapes, uppercase \U, and named HTML entities are not interpreted.
- Malformed escapes, unpaired surrogates, out-of-range values, ISO controls, and directional override/isolate controls remain visible as entered.
- Output remains plain text, including any decoded angle brackets.

## Ownership and performance

ChapterEscapeDecoder is pure Kotlin. ReaderTextController belongs to one ReaderViewModel and prepares text on Dispatchers.Default. Preparation checks coroutine cancellation during scanning and between paragraphs. A generation counter prevents old work from publishing after a chapter or preference change.

Unchanged strings and chapters reuse their original objects. The reader retains at most two reusable prepared entries, with an estimated 1 MiB UTF-16 text budget counting original and prepared strings. This is a reuse budget, not a cap on total process heap or on the currently displayed chapter. An oversized current result can be displayed but is not retained for later reuse. There is no persistent transformed cache, timer, polling loop, or whole-library scan.

Identical refreshed Chapter objects can be conflated by StateFlow. Resolution therefore accepts exact structural equality as well as object identity; changed content invalidates preparation even when the chapter ID is unchanged.

While preparation is pending, the same paragraph rows show original text. Copy, AI generation, narration launch, and auto-scroll start wait for prepared text. Initial scroll restoration and scroll checkpoints use the immediately visible raw text, so decoding never delays reading or overwrites a position already reached by the user. A real representation change pauses auto-scroll and retains the visible paragraph anchor, resetting its pixel offset for the new wrapping only after dragging, flinging, fast scrolling, and pull release have settled. Paragraph count never changes.

Preparation has a cooperative two-second deadline. Timeout, unexpected failures, and results containing only whitespace fall back to the original chapter with a visible message. Allocation-failure handling drops reusable entries and attempts the same fallback; this is not a guarantee that Android can recover from process-wide memory exhaustion. Disabling then re-enabling the option permits another preparation attempt.

No database migration, runtime permission, production dependency, provider credential, or additional remote request is required.

## Relevant files

- data/text/ChapterEscapeDecoder.kt: grammar, source allowlist, cancellation points.
- feature/reader/ReaderTextController.kt: preparation state, cancellation, bounded reuse, fallback.
- data/preferences/ReaderSettings.kt and ReaderPreferencesManager.kt: default, persistence, reset.
- feature/reader/ReaderViewModel.kt and ReaderScreen.kt: effective AI/copy/display text and scroll integration.
- feature/reader/components/ReaderQuickSettingsSheet.kt and feature/settings/ReadingBehaviorPage.kt: synchronized switches.
- data/narration/NarrationService.kt: preparation for newly started audio sessions.

## Verification

Verified on 2026-11-12:

- Debug app and Android test APK builds: successful.
- Focused offline unit/integration suite: 82 passed, zero failures.
- Pixel 8 API 36 emulator suite: 17 passed, zero failures.
- Git diff whitespace check: passed.

Two existing Android test interactions were updated for the current UI: the auto-scroll card now lives under Size & Spacing and its speed button is labeled Faster speed; the note guard test invokes the scrim click action because the expanded editor covers its center. No dismissal behavior was changed for these test corrections.

Focused offline tests cover valid/malformed escapes, supplementary characters, control preservation, literal backslashes, single-layer decoding, source exclusion, unchanged original objects, cancellation, stale work, equal-data refresh, changed-content refresh, reusable-cache eviction, synthetic preparation/allocation failures, persisted/reset preference, effective AI input, and auto-scroll targeting.

Android tests use a Pixel 8 API 36 emulator and isolated preferences/offline repositories. They exercise both switch locations, visible decoded text, paragraph-anchor preservation, and the existing quick-sheet/auto-scroll interactions. Live source/AI/narration calls are not part of these checks. Physical-device memory/thermal profiling and real cloud playback remain separate verification.

## Reader/navigation reliability follow-up

Reader chapter loading now validates that successful responses contain nonblank text. An empty/whitespace response produces the existing retry state rather than a title-only page. Superseded foreground loads and their startup content tasks are cancelled, keeping abandoned requests out of the source queue. Independent prefetching is retained. Foreground request generations also prevent an older refresh/selection result from overwriting a newer request; a late speculative prefetch cannot replace foreground text already loaded. Navigation publishes the new chapter and its loading/error state together, and outgoing chapter indices cannot drive a second pull.

Optional decoding does not gate pull navigation. Raw loaded content remains usable while preparation runs. Re-anchoring no longer calls stopScroll on a live gesture, and opening an overlay or losing navigation eligibility clears any outstanding pull translation. These are reader state/gesture changes; source URLs, parser selectors, source rate limits, and network retries are unchanged.

Regression coverage includes adjacent escapes such as \u003cAbility: None\u003e (plain output <Ability: None>), controlled slow preparation, whitespace-only decoded output, empty successful fetches for both sources, out-of-order refresh/prefetch completion, and real next/previous pulls with decoding changed while the pointer is held down.

Final follow-up verification: app/test APK builds succeeded; 82 focused offline tests and all 17 Android interaction tests passed against the final app APK. The Android suite includes both sources with a decoder held across user scrolling and next/previous pull gestures. The controlled UI tests inject a longer preparation deadline to isolate gesture behavior from emulator startup; the production two-second timeout is verified using virtual time. The software-rendered emulator encountered System UI ANRs during earlier runs; the complete final suite passed with host GPU rendering. No production behavior was changed to mask those emulator errors. Live website requests and physical-device profiling were not repeated.
