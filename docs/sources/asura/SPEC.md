# Asura Scans source specification

Verified against public pages on **27 October 2026**. This is a fetching/state implementation;
the separate manhwa screen is intentionally left for the UI agent. See [UI_AGENT_PROMPT.md](UI_AGENT_PROMPT.md).

## What changed

The previous `remote/AsuraSource.kt` fetched the obsolete `asuracomic.net` homepage,
invented IDs/ratings/genres and substituted `SeedData`/Unsplash covers when requests failed.
It did not load real details or chapter images. That file is now a compatibility typealias.
The implementation lives in `remote/asura`, following the other sources' organization.

Discover, Browse and Search use the existing `NovelRepository` and source tab. Clicking a record
still calls `rememberNovel` before navigation. The existing detail ViewModel now resolves
`/comics/…` correctly, including a cold link with only an Asura ID. No new screen is designed here.
Until the UI agent registers `ManhwaReader` and updates reader entry points, the old text reader
will explicitly reject image content. Do not interpret a successful backend build as a finished manhwa UI.

## Observed public methods

| Purpose | URL/contract |
| --- | --- |
| Home | `https://asurascans.com/` |
| Catalog | `/browse`, `/browse?page=2` |
| Category | `/browse?genres=fantasy` (genre **slug**, not numeric ID `16`) |
| Search | `https://api.asurascans.com/api/series?search=zenith&sort=latest&order=desc&limit=50&offset=0` |
| Detail/full chapter catalog | `/comics/childhood-friend-of-the-zenith` |
| Public chapter manifest | `https://api.asurascans.com/api/series/childhood-friend-of-the-zenith/chapters/1` |
| Chapter HTML fallback | `/comics/childhood-friend-of-the-zenith/chapter/1` |
| Original images | HTTPS `cdn.asurascans.com/asura-images/…`; send the chapter's `Referer` |

The public site adds a deployment suffix (observed `-3ebcc5a9`) to series links. Both the clean
series URL and `/s/2128` redirected to the same canonical public series during verification.
This integration uses the clean slug identity, not an array index, title hash or deployment suffix.
Accepted legacy host/path forms normalize to the current `/comics/…` URL. Earlier fabricated IDs
without a valid series URL cannot reliably be mapped to a real comic; no guessed title lookup is used.

API methods/parameters were checked against the site's own `BrowseFilters`, `SearchBar` and
`ChapterReader` scripts. Those hashed script filenames are **not** production endpoints or parser keys.
There is no dependency on a browser, WebView, JavaScript engine, third-party scraping proxy or API key.

## Parsing contracts and maintenance locations

| File | Responsibility |
| --- | --- |
| `AsuraUrls.kt` | Origin allowlists, clean IDs, deployment-suffix normalization, chapter numbers, query encoding |
| `AsuraSelectors.kt` | Semantic Astro component names and metadata selectors |
| `AsuraUtils.kt` | Bounded-depth Astro serialized props decoding; HTML/challenge detection |
| `AsuraParser.kt` | Pure mapping of captured site data to domain models |
| `AsuraModels.kt` | Cache/response limits |
| `AsuraErrors.kt` | Explicit blocked, locked, account, quota, network, parse and size failures |
| `AsuraHttp.kt` | Cancellation, response ownership, bounded reads, redirects, rate/concurrency limits |
| `AsuraSource.kt` | Caches, request coalescing and `NovelSource`/`ManhwaSource` adapter |
| `data/model/Manhwa.kt` | Typed details, chapter metadata, ordered page manifest |
| `data/manhwa/ManhwaImageStore.kt` | Original image file acquisition and decoder leases |
| `feature/manhwa/ManhwaReaderViewModel.kt` | Independent reader loading, navigation, progress, bookmarks and restoration |

Home/browse/chapter catalogs use server-provided Astro props (`astro-island[props]`) keyed by
component names such as `BrowseFilters.` rather than CSS class hashes. Astro's tagged JSON
is decoded as data, never evaluated. Detail metadata uses `ComicSeries` JSON-LD plus the full
`#description-text`, source status/type labels, alternative titles and rank. Partial chapter
catalogs, duplicate chapter numbers and wrong-series responses fail instead of entering cache.

Home has a **separate prose-novel feed** alongside comic feeds. It is excluded. Latest Updates
contains several recent chapters per comic; the comic's first occurrence is retained in site order.
No per-card detail fan-out is performed to fill missing home metadata or ratings.

## Metadata and identity

- Shared `Novel`: stable `asura-{clean-slug}` ID, original cover URL, full detail synopsis,
  author, genres, actual status, rank when provided, chapter count and source URL.
- Source ratings are out of **10**; common app ratings are normalized to **5**. `ManhwaDetails`
  retains `sourceRating` and `ratingScale=10` so a dedicated UI can display the original scale.
- Additional detail fields: artist, alternative titles, source series ID, media type and bookmark
  count when exposed. Optional fields remain empty/unknown when absent; rank `0`/rating `0` mean unavailable.
- Source view counts are retained where provided (for example in API search/home rows). The observed
  detail page labels bookmark totals, not an authoritative view total; these are not substituted for views.
- `NovelIdentity`, `BookmarkManager`, repository caches and the detail/Home URL resolver agree
  on the same canonical identity across Discover, Browse, Search, saved bookmarks and cold navigation.
- Existing bookmark records remain stored. Matching valid old series URLs normalize when loaded;
  fake numeric/sample URLs may return not-found. Old non-live database feed sections are excluded.
- The initial backend needed no schema change. Manhwa page-position persistence now uses Room
  version 5 with the additive `MIGRATION_4_5`; existing chapter history, novels and notes are retained.
  No new Android permission or dependency is needed.

## Chapters, ordering and access

`ManhwaDetails.chapters` is chronological, including chapter `0` and decimal numbers. It retains
premium flags and publication/unlock timestamps. The common details screen receives metadata-only
`Chapter` adapters; its `paragraphs` list is empty by design.

`getManhwaChapter` returns a `ManhwaChapterContent` with **pages in the exact source array order**,
zero-based indices, original URLs (including query parameters), dimensions, previous/next URLs
and image headers. Do not alphabetically sort URLs or deduplicate repeated pages. A declared
nonzero page count must match the returned array; invalid/missing/foreign image URLs fail the manifest.

The public chapter API is preferred. Only a 404 triggers the known HTML `ChapterReader` fallback.
There are no fallback cascades for 403/429/5xx, no retry loop, no URL guessing, login/unlock request,
Cloudflare challenge bypass or account cookie injection. `is_locked` and `access_gate` are checked
before exposing pages, including if a response also contains preview image data. API access and
future site availability are controlled by the source; this is not a promise of permanent access.

## Cache, concurrency and images

- Metadata and page manifests have a 5-minute in-process TTL and bounded LRU capacities: one
  home, 24 detail catalogs (at most 12,000 cached chapters in total), 24 browse pages, 24 searches,
  eight chapter manifests. Striped request
  locks coalesce identical operations without an unbounded lock map.
- At most two metadata requests run concurrently, with a 600 ms minimum interval shared by the
  site's HTML/API origins. Each has a 30-second call timeout. Responses are limited to 8 MiB,
  including unknown-length streams; every success/error/redirect body closes. Cancellation cancels OkHttp.
- Known cache data can remain visible through the common repository's stale browse/feed handling.
  No production sample feed is substituted for a failed Asura request. Forced detail/catalog/chapter
  refreshes bypass the matching source/HTTP cache. Asura is excluded from the repository's old
  non-expiring `>100 chapters` cache shortcut.
- `app.manhwaImageStore` is a singleton for original image files. `acquire(page, chapterUrl)` is
  suspendable and returns a `ManhwaPageLease`; **close it only after the decoder is released**.
  The dedicated cache is 128 MiB/512 files, each image is capped at 24 MiB, and at most two stream downloads
  run at once. An 8 KiB transfer buffer avoids whole-image byte arrays. HTTP's separate disk cache
  is disabled for these downloads to avoid storing each image twice.
- Only validated image MIME/magic headers are accepted (WebP/PNG/JPEG); redirects are refused.
  Temporary files are renamed only after successful download. Failure/cancellation cleans them up.
  Cache hits coalesce, eviction skips leased files, and insufficient free capacity reports an error.
  In-flight temporary files can add at most two image limits above the completed-file budget.
- Settings' existing Clear cache action clears inactive manhwa files through their owner. Active
  images remain protected. Lease closure is idempotent; a cancelled acquisition releases a lease
  created just before coroutine handoff. Process-start initialization removes abandoned partial files.
- These are disposable caches, **not an offline download library**. Android may remove cache files.
  Page manifests are not persisted as a permanent chapter download database.

**Tall images require tiled/region decoding in the UI.** The observed chapter contains 800×15,555
images (roughly 47 MiB each if fully decoded into ARGB). Neither the source, ViewModel nor image store
allocates a bitmap. Do not hand these files to an unrestricted full-size bitmap loader. Limit visible
tiles, cancel offscreen work and release region decoders/leases; see the UI prompt.

## Reader state contract

Construct `ManhwaReaderViewModel` with the navigation novel ID, shared repository/bookmark/progress
managers, `SavedStateHandle`, and optional zero-based `initialChapterIndex`. Use a navigation-entry
ViewModel factory, not a plain `remember` owner.

- `uiState`: loading, empty, success and typed error/locked states; current chapter/content and
  restored page/fraction; bookmark state. It contains URL metadata only.
- `selectChapter`, `nextChapter`, `previousChapter`: one active chapter request; a newer explicit
  selection cancels/invalidates old results. Repeated Next/Previous while loading do not skip chapters.
- `refreshCatalog`, `retry`, `retryChapter`: explicit user actions, no background request/retry loop.
- `savePagePosition(chapterId, pageIndex, fraction)`: call on idle/page changes, not every frame.
  IDs reject stale callbacks from an outgoing chapter; values are clamped/validated.
- A successfully validated, nonempty chapter manifest calls `recordManhwaChapterOpened` and marks
  exactly that chapter read, matching the prose reader. Detail/Home progress, dimmed chapter rows
  and sidebar ticks observe the same read-ID set. Failed, locked and stale requests do not mark read.
- `markChapterFinished(chapterId)`: only after the last image is successfully displayed and its end
  is reached. This records the final page position separately from the opened/read badge.
- Manhwa progress uses `ReadingProgressManager.recordManhwaChapterOpened`, `saveManhwaPagePosition`
  and `markManhwaChapterCompleted`; the prose `markChapterRead` contract remains unchanged.
  `ManhwaPagePosition` stores page index, image-relative offset and page count in Room. SavedStateHandle
  handles task recreation; Room handles explicit reopening after a removed task/process restart.
  Restoration waits for real image layout before applying the offset and yields to manual scrolling.
  Save on scroll idle, background/lock and disposal; no per-frame position or database writes.
  Completion accepts the visible footer after the loaded last image leaves the viewport. Read counts
  use distinct opened chapter IDs, never infer earlier chapters, and do not increase on reopening.
  Home-first cold launch is retained.
- The UI follow-up registered `NovelNestDestination.ManhwaReader`. Both reader routes now use
  `ReaderEntryContent` to resolve the source before creating either reader ViewModel. Existing
  text-reader routes containing Asura IDs render the manhwa reader in place, without redirects.
  Stored source metadata takes precedence over cold-route prefix hints. Outgoing entries cannot
  overwrite the active page's source preference.
- Detail observes bookmark changes made in Reader and progress under its resolved canonical ID.
  For legacy incoming IDs it also considers that history, keeps the newer reading position and
  combines read IDs for display; it does not rewrite stored history or migrate the database.

## Verification

See [VERIFICATION.md](VERIFICATION.md) for the completed build/test results and remaining device/UI checks.

Fixtures are public anonymous captures, with scripts/styles/SVG/comments removed to reduce noise.
They retain the real Astro props, SSR DOM and JSON-LD. `search-empty.json` records the API's genuine
`data:null` empty-result shape. `locked.json` records the access state at capture time; it does not
claim that the same chapter remains locked indefinitely. No full chapter image is committed.

Tests cover metadata/identity across entry routes, category slugs, completed status, exact API/HTML
image order, repeated pages, missing/changed selectors, partial/wrong chapter rejection, locked access,
URL policies, cache TTL/force/coalescing, cancellation/body closure, image leases/cache budgets,
reader restoration/stale results, bookmarks, opened/read status and independent end positions. The live smoke check
also fetches a real original WebP image using the returned Referer.

Run offline fixture checks:

```powershell
.\gradlew testDebugUnitTest --tests 'com.novelnest.app.data.remote.asura.*' --tests 'com.novelnest.app.feature.manhwa.*'
```

The live test is skipped unless explicitly enabled:

```powershell
$env:ASURA_LIVE_SMOKE = '1'
.\gradlew testDebugUnitTest --tests 'com.novelnest.app.data.remote.asura.AsuraLiveTest' --rerun-tasks
Remove-Item Env:ASURA_LIVE_SMOKE
```

Do not use old cached test reports as a live availability result. Device rendering/tiling performance,
accessibility, rotation, scrolling and image retry behavior need verification after the UI is built.
