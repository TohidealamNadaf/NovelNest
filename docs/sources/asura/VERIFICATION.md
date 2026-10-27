# Asura backend verification

Verified on **27 October 2026**. This report covers the source, repository, image-file cache and
UI-independent reader state. The dedicated manhwa screen is intentionally pending; use
[UI_AGENT_PROMPT.md](UI_AGENT_PROMPT.md) for that implementation.

## Results

| Check | Actual result |
| --- | --- |
| Debug application APK | Built successfully |
| Debug Android test APK | Built successfully; not executed on a device |
| Scoped unit/regression suite | 138 tests, 135 passed, 3 skipped, 0 failures/errors |
| Separate opt-in live source test | Passed; 1 test, 0 skipped/failures/errors |
| Whitespace/diff check | Passed |
| Android lint | Blocked by one existing theme API-level error; details below |

The skipped offline-suite tests are opt-in live checks. The Asura live check was separately enabled
and executed against anonymous public endpoints, not inferred from fixtures or cached reports.
It returned **4 home sections, 20 browse results, 112 chapters and 14 ordered pages** for the selected
series/chapter. It also downloaded a genuine original WebP through `ManhwaImageStore` using the
chapter Referer and verified the file header. These counts describe that observation, not fixed
production totals. No credentials, unlocking or challenge bypass were used.

## Coverage

- Real captured homepage, category, pagination, search, detail and chapter payloads; complete synopsis,
  metadata, completed status, stable identities and chronological/decimal chapter numbers.
- Chapter API and HTML fallback page order, repeated images, dimensions/query strings, wrong-series
  and partial-response rejection, and locked/account-required access states.
- TTL, force refresh, request coalescing, weighted chapter-cache bounds and rejection of legacy sample
  data during source failures. No fabricated production records are used to hide a failure.
- Transport size/origin limits, response closure, coroutine cancellation and restricted redirects.
- Image cache budgets, leases, eviction/clear protection, cancelled downloads and temporary-file cleanup.
- Reader restoration, rapid/stale chapter selection, explicit retries, bookmarks and the distinction
  between opening a chapter and completing it.
- Existing NovelPhoenix/FreeWebNovel parsers and routing, browsing, ratings, identity, reading history,
  profile/notification behavior and the repository's Asura cases.

The final scoped Gradle run included `assembleDebug`, `:app:assembleDebugAndroidTest` and
`:app:testDebugUnitTest`, with `--offline --warning-mode fail --max-workers=1`. The existing ignored
verification init script/project cache isolated build outputs under `build/ai-nvidia-isolated`.
Normal fixture and live-test commands are documented in [SPEC.md](SPEC.md).

## Existing lint issue

`app/src/main/res/values/themes.xml:15` sets `android:windowLightNavigationBar` in the base resources.
Lint reports API 27 is required while the app supports API 26. The same line exists in the starting
revision. Lint reported **1 error and 110 warnings**, with no findings in the new Asura/Manhwa files.
This feature does not suppress that error or modify unrelated theme resources.

## Remaining verification

- Build/register the dedicated screen and route every reader entry point using the UI prompt.
- Verify tiled/region image decoding on physical devices, especially the observed 800-by-15,555 images.
  Backend file streaming does not establish a safe bitmap/rendering memory budget by itself.
- Exercise scrolling, restoration/rotation, themes/insets, accessibility, per-image retry, cancellation
  on navigation, lock/unlock changes, slow/offline networks and Settings cache clearing during reading.
- Profile frame times, peak memory and thermal behavior after the UI exists. No zero-heat or
  all-device performance guarantee has been made.

External site contracts and access restrictions can change. See [MAINTENANCE.md](MAINTENANCE.md).

## Detail/routing follow-up

The separate UI was subsequently added. This follow-up fixes source dispatch and retained Detail
state without redesigning its layout:

- Both reader routes resolve source metadata before creating a reader ViewModel. Legacy routes render
  the correct reader in place; there is no asynchronous redirect or duplicate back-stack entry.
- Source prefixes are fallback hints, not overrides for known source metadata. A changed novel ID
  resets source resolution immediately. Lookup failure exposes explicit Retry/Back controls.
- Detail observes canonical and legacy reading progress, displays the newer position and the combined
  read IDs, and observes bookmark changes made in Reader. Saved database history remains unchanged.
- Scoped regressions include source selection/cancellation, delayed identity resolution, legacy
  progress, live bookmark changes and sorted/filtered chapter indices across all three sources.
  The expanded suite reports **152 tests: 149 passed, 3 opt-in live checks skipped, 0 failures/errors**.
- Added Compose/NavHost interaction tests cover dispatch before initialization, Back during lookup,
  explicit retry and identity replacement. They are compiled into the Android test APK; no device
  was connected for execution. Physical-device navigation/lifecycle verification remains pending.

## Manhwa progress follow-up

- Separate manager methods now record an opened manhwa chapter, persist its page/offset on idle
  or background, and mark an explicitly completed chapter. Prose calls keep their prior behavior.
- Fixed ignored page offsets and missed completion when the footer is visible but the last image
  has left the viewport. Page readiness is reported after real image layout replaces the placeholder.
- Room schema 5 adds only three defaulted page-coordinate columns to `reading_progress`;
  exported schemas were compared to confirm other tables remain unchanged. No destructive fallback.
- Debug application and Android test APK builds passed. Scoped unit/regression suite: **159 tests,
  156 passed, 3 opt-in live checks skipped, 0 failures/errors**. Checks cover persistent restore,
  duplicate writes/completion, stale/invalid callbacks, page/footer geometry and prose isolation.
- Added device migration coverage for 4-to-5 and updated the oldest-version migration chain. Those
  tests compile; executing migrations and visual scrolling/restore on a physical device remains pending.

## Read-status correction

- The previous end-only read policy differed from the prose reader: opening a valid chapter updated
  Continue Reading but left `readChapterIds` empty until the image-end callback. Detail progress,
  dimmed chapter rows and sidebar ticks all depend on those IDs.
- `recordManhwaChapterOpened` now marks exactly the validated chapter read, matching prose behavior
  through the separate manhwa API. Reopening does not increase counts; jumping to chapter 83 does
  not mark chapters 1–82 read. Failed, locked and stale responses do not mark chapters read.
- End detection still records the final image position even when the chapter already has a read
  badge. Page restoration remains separate; no UI redesign or additional schema change was needed.
- A regression exercises the real Detail and Manhwa Reader ViewModels against the same progress
  manager, checking immediate read IDs, nonzero Detail progress, jumping/reopening, locked failure
  and database reload. Existing tests also verify prose isolation and saved image coordinates.
- `assembleDebug` passed. The expanded offline regression suite reports **160 tests: 157 passed,
  3 opt-in live checks skipped, 0 failures/errors**, with `--warning-mode fail --max-workers=1`.
  Physical-device visual confirmation remains pending. Previously unrecorded history is not guessed;
  successfully reopening those chapters will record their read status.
