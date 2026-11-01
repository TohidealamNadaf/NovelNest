# Copy this prompt to the UI agent

**Integration follow-up:** The UI is now present. `ReaderEntryContent` resolves the stored source
before instantiating either reader, and both registered reader routes use it. Preserve that
in-place compatibility dispatch; do not reintroduce a redirect after creating `ReaderViewModel`.
Detail also observes live bookmarks and canonical/legacy progress. The remaining instructions
describe the original UI handoff and continue to apply to screen maintenance.

Implement the dedicated Asura manhwa UI in this existing Android/Compose app. The fetching,
identity, image-file acquisition, bookmarks and reader state layers are already implemented.
First read `docs/sources/asura/SPEC.md` and the files it maps. Preserve the current NovelPhoenix,
FreeWebNovel and prose-reader behavior. Use the established NovelNest themes/components.

## Your scope

Design/build a separate image reading screen and wire it into existing navigation. Improve only
the Asura-specific UI/data presentation needed for this task. Do not implement scraping inside
composables, replace production data with sample manhwas, fork the existing source/cache ownership,
or change the text reader's quick sheet/auto-scroll gesture policies.

## Existing backend contracts

1. `NovelRepository`:
   - Existing `getHomeSections`, `getBrowsePage`, `searchNovels`, `getPopularNovels` now return live Asura data.
   - `getManhwaDetails(ASURA_SCANS, seriesUrlOrId, forceRefresh)` returns `ManhwaDetails`.
   - `getManhwaChapter(ASURA_SCANS, chapterUrl, forceRefresh)` returns `ManhwaChapterContent`.
   - `rememberNovel(novel)` must happen before card navigation. The app's shared card callback already does this.
2. `data/model/Manhwa.kt`: details, chronological chapter list, original source rating/scale,
   locked/premium metadata, and zero-based page records in exact display order with dimensions.
3. `feature/manhwa/ManhwaReaderViewModel.kt`: instantiate per navigation entry using the app's shared
   repository, `bookmarkManager`, `readingProgressManager`, `extras.createSavedStateHandle()` and route chapter index.
   Observe `uiState` with lifecycle-aware collection. UI actions call the existing methods; no page fetch in recomposition.
4. `NovelNestDestination.ManhwaReader`: `manhwa-reader/{novelId}?chapterIndex={chapterIndex}`.
   Its screen is now registered in `NovelNestNavHost`; both reader routes use `ReaderEntryContent`.
5. `NovelNestApplication.manhwaImageStore` is the single original-image file owner.
   `acquire(page, content.chapter.url)` suspends and returns `ManhwaPageLease.file`.
   Hold the lease for the decoder's entire lifetime, then close it in cancellation/disposal cleanup.
   Do not create another image store per page/screen or use a second network/disk cache for these images.

## Navigation: cover every entry route

- Discover/hero/trending/popular/latest cards, Browse/category/pagination and Search should continue
  using common card->rememberNovel->Detail routing. Asura detail metadata/chapter fetching is already wired.
- Existing details may be reused or given a source-specific composable using `ManhwaDetails` for
  artist, alternative titles, premium state and original rating. Keep the existing bookmark action working.
- Detail Start/Continue and chapter-row clicks must open **ManhwaReader**, not `ReaderScreen`.
  Pass the index in the chronological source list, not the visible reversed/search-filtered list position.
- Home Continue Reading, recently read/bookmark cards, existing reader routes restored from earlier
  versions and any reader deep links must dispatch to the correct reader by resolved source.
  Both reader routes already resolve repository/bookmark metadata and render the correct reader
  in the existing navigation entry. Keep this compatibility behavior; no redirect or text-reader
  initialization should occur for Asura. `asura-` is only a cold-route hint when metadata is absent.
- Preserve Home-first cold launch and selected-source persistence. No automatic reader reopen at app launch.
- Save the actual Asura source in navigation preferences on a live manhwa reader entry, matching the
  existing reader's protection against outgoing destinations overwriting a newer source choice.

## Reader experience and performance constraints

- Preserve `ManhwaSystemBars`: controls and Android bars show/hide together, with restoration when
  the reader loses its resumed lifecycle or leaves composition. Keep status-bar padding inside the
  header Surface so its background reaches the screen edge; retain fixed insets during its exit animation.

- Build a vertical webtoon/image reader with chapter title, back/chapter list controls, progress
  and explicit Previous/Next. Handle long titles, all reader themes, status/navigation bars and font scaling.
  If reusing `ReaderChaptersDrawer`, pass its `isOpen` state so it defaults to the active chapter
  whenever opened, matching the existing reader fix. Account for `LocalNarrationBottomPadding`
  if the global mini-player is visible, without coupling manhwa state to narration playback.
- Use lazy page composition with stable chapter+page keys. Preserve source page order exactly;
  neither sort URLs nor deduplicate repeated images. Derive aspect ratios from width/height to reduce layout jumps.
- **Mandatory: tiled/region decoding for tall images.** A verified page is 800×15,555 and would consume
  about 47 MiB as one ARGB bitmap. Render only visible bounded tiles from leased original files, using
  `BitmapRegionDecoder` or a vetted compatible subsampling library. Check any new dependency against
  the actual Kotlin 2.0.21/Compose UI 1.8.2/Material3 1.4.0/minSdk26/target35 project. Do not upgrade
  the app's Compose/Kotlin/AGP versions merely to add the screen. If using framework decoding, run it
  off main, limit decode concurrency, guard decoder disposal races, and recycle offscreen tiles/decoders.
- Keep image file acquisition and decoding cancellable. Do not download/decode the whole chapter,
  prefetch many chapters, allocate a chapter-wide bitmap/byte array, or force full-resolution Coil decoding.
  Covers/thumbnails may continue to use the existing Coil image loader.
- Limit retained image leases and decoded pixels to the visible viewport plus a small bounded buffer.
  Acquire in a lifecycle-owned effect; use `try/finally` so cancellation between acquisition and display
  still releases the lease. Close/recycle the decoder before closing its file lease.
- Show per-page loading/error/retry; one failed image must not blank already loaded pages or create
  an automatic retry loop. For 401/403/404/stale image URLs offer an explicit chapter refresh through
  the ViewModel. Release old page leases before a forced same-file refresh. Never bypass source locks.
- On Home/lock/leaving the screen, stop offscreen acquisition/decoding; do not start a service/wake lock
  or battery exemption. Handle recomposition, rotation, removed task/process death and fast chapter changes.
  Use lifecycle cancellation (for example `repeatOnLifecycle(STARTED)`) for image resources; a plain
  `LaunchedEffect` alone does not stop when an Activity is backgrounded but remains composed.

## State, progress and actions

- Render loading, empty, successful, network, blocked/429, format-change, not-found, locked and account-required states.
  Show the backend's actionable message and unlock time if present. Retry calls `viewModel.retry()`;
  manual catalog refresh calls `refreshCatalog()`. No hidden account/key/auth setup is required.
  `isPremium` is metadata, not a permanent read prohibition: the chapter API's current locked/access
  result is authoritative, including after an early-access timestamp has passed.
- Do not allow rapid Next/Previous to create parallel requests. The ViewModel already coalesces these
  while loading, and explicit chapter-list selection cancels/invalidates the previous request.
- Restore the ViewModel's `currentPageIndex` and `pageOffsetFraction` once the selected content's layout
  is ready. Keep restoration keyed to chapter identity; do not reset scroll on every state emission.
- Report `savePagePosition(content.chapter.id, index, fraction)` at page changes/scroll idle or disposal,
  never write SavedStateHandle/preferences every animation frame. Reject old outgoing-content callbacks.
  Flush on background/lock as well as disposal. Dedicated manhwa progress methods now persist page
  coordinates in Room schema 5; do not call the prose `markChapterRead` method from the image reader.
  Restore the image-relative offset after real page layout, and retain the loaded-last-image gate
  when detecting completion from either that image's end or the visible chapter footer.
- Call `markChapterFinished(content.chapter.id)` only after the final image is loaded and its bottom
  actually reached; this saves the end position. Read badges are already updated by the ViewModel
  after a valid chapter manifest opens, through the dedicated manhwa progress API. Do not gate
  read badges on this end callback or mark chapters from lazy item composition.
- `toggleBookmark()` uses the shared bookmark manager. Updating bookmarks/progress must immediately
  reflect on Home without creating another manhwa-specific bookmark/progress database.
- Do not expose prose-only Summary/Rewrite/Narration/Markdown/auto-scroll controls in the image reader.
  Do not pass image URLs as chapter paragraphs or send image data to the prose AI/narration pipeline.

## Verification and handoff

Add UI/device tests for every entry route, correct selected chapter in ascending/descending lists,
long/tall image rendering, image failures/retry, rapid chapter changes, cancellation/disposal,
rotation/restoration, bookmarks/Home Continue history, locked chapters, empty lists and source switching.
Run existing novel/source/reader regressions and both APK builds. Use real source responses for live
checks, and keep deterministic fixtures for routine tests. Profile a real phone's peak bitmap memory,
frame times and sustained scrolling before claiming performance or thermal safety.

Deliver a list of files changed, verified scenarios, actual test results, remaining device checks and
any new dependency/configuration decisions. The fetching backend has fixtures/tests; use them rather
than duplicating endpoint/selector logic inside the UI.
