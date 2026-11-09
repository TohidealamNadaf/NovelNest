# Architecture reliability repairs ? 1 November 2026

## Changes

1. Repository cache writes now use one guarded application-owned launch boundary. Expected storage failures are logged by type, cancellation is rethrown, and the scope has a final uncaught-exception handler. Feed metadata and cross-references update in a Room transaction; obsolete source feed rows are removed only after a successful nonempty live response. User history, bookmarks, notes and terms are not deleted by this operation.
2. Phoenix listing failures, blocked access and changed selectors now return failures instead of sample titles. Repository feed/search recovery uses real cached records or errors. Detail/Reader metadata lookup no longer consults SeedData. New Phoenix feed IDs carry a live-data version; old sample feed sections are excluded until a live refresh. Explicit sample-reader routes remain available and are not a source-error fallback.
3. ReadingProgressManager retains its existing mutex and history merge. Reads/writes have at most three attempts with 250/500-ms delays. Exhausted failures are observable through persistenceState. A failed initial load blocks replacing database history; recovery merges stored and live read IDs before writing. Foreground entry retries failed saves without network requests or a repeating retry timer.
4. Chapter is defined in data/model, so source parsers, repositories and caches no longer import the reader feature. A reader-package type alias preserves compatibility for existing UI integrations. Imports and test fixtures use the shared model explicitly.
5. Quick Settings subpages were extracted into ReaderSummaryPage, ReaderNotesPages, ReaderExtractionPages and ReaderAiActions. The main file fell from 3,331 to approximately 1,326 lines. Preflight SHA-256 checks and full source reconstruction verified that the extracted bodies were unchanged; only cross-file visibility changed. Sheet navigation, note drafts, dismissal guards, animation and auto-scroll handoff remain owned by the original sheet. Layout and callbacks were preserved.
6. The application injects a process-owned IO bookmark writer. It keeps the active snapshot and one latest queued immutable snapshot, encodes JSON off the UI thread, confirms writes with commit, and exposes bounded failure/retry state. The legacy JSON format and initial identity/hydration logic are retained; no Room migration is introduced. The synchronous compatibility constructor remains available for standalone consumers.
7. Feed freshness now reads persisted section timestamps after process recreation. L1 expiration does not extend past the persisted L2 freshness boundary. Empty successful feeds cannot replace a usable stored feed.

## Verification

- Debug app APK: assembleDebug passed.
- Android interaction/migration test APK: assembleDebugAndroidTest passed; compiled only, not installed/run on a device.
- Selected offline regressions: 151 tests across 22 suites; 148 passed, 3 opt-in live source checks skipped, 0 failures/errors.
- Nine new regressions cover transient/permanent progress-save failures, failed-load history preservation, explicit recovery, bookmark snapshot coalescing and bounded retries, blocked/changed Phoenix responses, and missing-source behavior.
- Existing selection covers source fixtures/routes, reader and manhwa progress/restoration, Detail, Home history, profile/notifications and system-design cache/catalog regressions.
- The first test compile exposed a wildcard-import ambiguity after moving Chapter; explicit shared-model imports resolved it. The final build and selected suite passed.
- git diff --check passed. No schema version, permission, credential, backend or provider configuration was changed. No live AI/narration generation or device installation was performed.

## Boundaries

Persistence state records failures for controllers/diagnostics; this change adds no new error banner or layout. Queued writes can still be lost if Android terminates the process before persistence completes. Bookmark loading retains the existing JSON format/initial hydration policy; this is not a Room bookmark migration. Initial legacy decoding and very large libraries still merit device measurement.

Physical-device visual/lifecycle checks, real disk-full/reboot fault injection, sustained heap/frame/thermal profiling, and execution of Room/device tests remain pending. Existing narration Notification.Action.Builder deprecation warnings are outside these changes. The selected tests are not an assertion that every application scenario or external site is verified.
