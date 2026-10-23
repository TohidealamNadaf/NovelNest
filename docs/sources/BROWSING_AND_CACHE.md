# Browse, chapter totals and cache behavior

## Data flow

Home reads BookmarkManager locally. NovelCard displays chapterCount, or a legacy field that explicitly says N Chapters. It never treats a chapter title or chapter number as a total. Unknown totals have no count badge. Details refresh bookmarked metadata without extra requests for each poster.

Discover keeps the existing source feeds. Browse fetches real NovelPhoenix and FreeWebNovel catalog pages. Categories come from source navigation links; selecting a category requests that URL instead of filtering unrelated feed records. Load more requests one next page and deduplicates novel IDs. Asura retains its existing feed/tag filtering.

All novel cards still use the common rememberNovel callback before detail navigation. Metadata, bookmark identity, chapter snapshots and content routes stay source-specific. Changing source/category cancels the prior job and a generation check rejects delayed results.

## Cache budgets

| Data | Budget | Freshness |
| --- | --- | --- |
| Browse pages | 40 pages, shared LRU, source + canonical URL key | 15 minutes |
| Source novel details | 128 records per source | 15 minutes |
| Source complete chapter lists | 16 novels per source | 15 minutes |
| FWN chapter pages | 64 pages | 15 minutes |
| Source chapter content | 64 chapters per source | Stable content; explicit refresh bypasses |
| Request locks | Fixed pools of 16 | No growing map of URLs |

Catalog and detail requests share locks so simultaneous callers can reuse one response. FWN uses separate lock pools for list and page operations to avoid nested-lock collisions. Browse cache expiry does not remove persistent bookmarks. On a catalog fetch failure, a previous cached page is returned with an explicit stale marker; failures do not overwrite successful cache entries.

Only complete chapter lists enter the complete-list cache. Progressive chapter snapshots are kept separately. Short complete Phoenix lists are cacheable; list size is not evidence that pagination remains unfinished. Failed first-chapter warming can be attempted again.

Chapter totals persist in bookmark JSON and Room schema 3. Migration 2→3 adds a non-null integer defaulting to zero; it does not delete tables.

## Regression coverage and limits

- FreeWebNovelRouteTest and NovelPhoenixRouteTest share SourceRouteContract: Discover, Browse All, category, Search and restored Home bookmark → metadata → chapter list → first/next chapter. The actual source/repository/parsers run against intercepted fixture responses. Detail/reader fixture identities are adapted to the selected real listing card; chapter pagination is deliberately shortened to one fixture page.
- Existing FreeWebNovelTest and NovelPhoenixParserTest cover captured parsing, full pagination metadata, empty/blocked pages, legacy IDs and partial-list failures.
- BrowseSourceTest covers actual captured catalog navigation, URL validation, source isolation, request coalescing, offline stale results, cache expiry/eviction, and Asura compatibility.
- BrowseViewModelTest covers rapid source switching, remote category selection, duplicate Load more actions, deduplication, warm category return and local library updates.
- ChapterCountPersistenceTest covers Room entity mapping and the migration SQL contract. It is not an on-device migration execution test.
- ChapterStartupTest covers progressive reader startup and concurrent prefetch/read requests.

Run the targeted offline suite with testDebugUnitTest and the class filters above. Live smoke tests are separate from deterministic regressions. Passing fixtures cannot guarantee that an external site will stay reachable or retain its layout. Device UI flows and device performance measurements still require an emulator or phone.

Public fetch verification during this change: NovelPhoenix Fantasy returned HTTP 200 and was captured in browse_fantasy.html. FreeWebNovel catalog requests received browser-verification responses from this environment, so live FWN catalog behavior was not certified.


## Reading progress, search identity and task restoration

Bookmarked posters show a bottom-edge progress line and a distinct-read/total chapter badge. Details show the same count. Totals use explicit source metadata; unknown totals display the number read. Read chapter IDs are deduplicated across relative/absolute URL forms. Reopening a chapter does not increase the count, and jumping to chapter 50 does not imply chapters 1–49 were read. The existing reader marks a successfully loaded/opened chapter as read.

ReadingProgressManager merges stored history with updates received during startup. Updates derive from the current map atomically, and serialized Room writes persist the latest state. Reader startup awaits local progress hydration before choosing a resume chapter.

Search deduplicates both database and remote records by source and canonical detail URL. Old IDs and tracking parameters cannot create extra cards for the same URL. NovelPhoenix listing titles prefer heading/title attributes; ranking labels stay separate. Valid empty remote searches stay empty.

The reader uses the navigation entry SavedStateHandle for its current chapter and scroll position. Scroll restoration waits for paragraphs and ignores outgoing chapter callbacks. Normal background resumes and Activity recreation retain this task state without forcing Home or reloading the reader. Fresh launcher entry, including after removal from Recents, opens Home instead of automatically navigating to the last reader.

NavigationPreferences persists only the last-used source enum name and initializes Home before its first frame. Tab actions across Home, Discover, Browse, and Search, plus novel/reader entry, record the source; unchanged values skip writes. The former reader_session checkpoint is removed once, migrating only a recognized source prefix when there is no valid saved source. No database migration, network fetch, per-chapter navigation write, or task-removal callback is involved. Room reading history and reader content caches remain independent; Continue Reading uses the saved chapter as before.

The synopsis uses pressed/focused surface feedback clipped to its rounded card instead of an expanding circular ripple; content expansion remains animated.

ReadingAndIdentityTest includes distinct read counts, startup/write ordering, canonical search identity, nested listing markup, valid empty results, reader SavedStateHandle recreation, and legacy-checkpoint/source-preference behavior. These are JVM tests; actual Android task recreation, scrolling and touch appearance still require device verification.


## Reader closure investigation

Code inspection found a deterministic reader list-key collision: paragraph keys used chapter ID plus String.hashCode(). Identical paragraphs, and different strings with equal Java hashes (for example Aa and BB), generated duplicate Compose lazy-list keys. The screen now keys paragraphs by chapter ID plus paragraph index, retaining every paragraph. Chapter transition reads are guarded, and the drawer uses position-qualified chapter keys.

A separate memory-retention defect kept full text in ReaderUiState for every chapter visited, independent of the bounded source caches. ReaderContentPolicy now retains full text only within two chapters of the current index (at most five chapters); all chapter metadata remains available. Responses arriving after a jump also apply that policy. Source caches can serve older chapters when revisited. Prefetch tracking is limited to in-flight work instead of retaining every prefetched chapter ID.

Unexpected content-fetch Exceptions now produce the existing retry state instead of escaping a reader coroutine; cancellation still propagates.

ReaderStabilityTest verifies repeated-text and hash-collision keys and retention boundaries. ReadingAndIdentityTest additionally exercises 150 chapter selections, reopening evicted text, failed prefetch and a thrown foreground fetch followed by retry. These regressions do not constitute an on-device heap profile or a captured production crash. No device/crash log was available to identify the exact observed closure event.


### Repeated swipe follow-up

The reported sequence was NovelPhoenix Search → Bookmark → Home → open chapter 83 (also observed from chapter 1) → repeated next-chapter swipes, with closure around the third/fourth transition. The reader gesture handler previously launched a coroutine for each drag update and allowed outgoing AnimatedContent callbacks to request navigation without identifying their chapter. Drag offset updates are now synchronous; only the release reset animates. A release clears its pull offset before navigation, and both the screen and view model require the originating chapter to be the current, loaded chapter. Loading, empty and outgoing chapter callbacks are rejected.

New regressions start at chapter 83, send duplicate callbacks across seven chapter changes, reject navigation during a delayed load, and verify a late prefetch after a jump cannot overwrite the current chapter or retain distant text. These tests exercise state/navigation logic, not physical touch dispatch on an Android device; a production stack trace is still required to identify the exact reported crash.


## Home reading sections

Home now contains, in order: a large Continue Reading card for the latest read novel, a horizontal row of up to ten recently read novels, and the complete bookmark grid ordered by most recent reading activity. All three respect the selected source. Unread bookmarks keep their existing bookmark order after read bookmarks. With no reading history, Continue Reading and Recently Read are hidden; the bookmarks/Discover empty state remains available.

The Continue button seeds the exact local novel record and resumes its saved chapter using the existing reader route. Other novel cards open details through the shared callback. Each reading card includes distinct read/total progress. A removed bookmark remains in reading history.

Home combines persisted reading progress with bookmark metadata and local repository/Room lookups. It fetches no discovery feed and makes no network detail requests. Resolved history metadata is bounded to ten records per source and reused as chapters are read. Missing legacy metadata receives a local title/placeholder cover so history remains navigable. Stable section/card keys and a single vertically scrolling lazy grid keep large bookmark libraries virtualized; the recent row scrolls horizontally.

HomeHistoryTest covers current/ten-recent selection, source isolation, read-first bookmark ordering, aliases, unbookmarked history, local metadata caching and updates after a chapter is read. Device layout and touch behavior have not been visually verified.


## Reading screen and library layout

Reading progress lines disable Material stop indicators and track gaps: the far-end marker was decorative, not a second progress value. Home uses three fixed columns; recent cards use the same available-width calculation and spacing. Poster badges use compact read/total counts. Continue uses one bounded Coil cover request for the poster and its ambient background, with an opaque gradient for text contrast.

Reader navigation bars have an opaque inset mask matching the selected reading theme. System navigation color and icon contrast also follow the theme, with app defaults restored on exit. This supports both edge-to-edge gesture bars and older Android navigation bars.

Home and Discover share the Novel Nest branding. Headers constrain titles and keep actions in separate space; search text stays on one line. Source indicators derive their equal widths from constraints without measured-layout state updates. Browse categories, results and recovery states share one scroll surface, leaving more room for results on small screens.

Footer hit targets remain stationary while icons animate. Returning Home pops to the existing Home entry and saves the tab being left, rather than restoring a potentially nested Home stack. Haptic platform failures are logged and cannot block actions. These address plausible intermittent navigation paths; a device trace is still required to confirm the originally reported intermittent freeze.


## Featured captions and source ratings

Featured slides bound the chapter caption to the space left after the Read action, with one-line ellipsis and a 48dp button target. Titles may use two lines; carousel height grows with the configured font scale. FWN chapter captions strip a repeated novel-title prefix.

FWN New Novels can report 0.0 on the source itself; that is displayed as an unrated dash, not a numeric reader score. Explicit positive source ratings are parsed from rating elements, skipping decorative spans and accepting decimal commas or /5 suffixes. Phoenix catalog parsing targets star badges rather than rank badges and no longer substitutes a fabricated 4.5 when the rating is absent. Browse reuses already-known scores from memory when a sparse record omits them, including cached/offline page reads, without issuing a detail request per card. No score is fabricated when the source and local metadata both lack one.


## Continue reader startup and durable resume cache

Cold Continue previously called the complete-catalog endpoint before loading content when its in-memory snapshot was missing. Parsed text also lived only in process memory. This made reopening much slower than next-chapter navigation, which benefits from prefetching.

Reader startup now consumes progressive catalog results and proceeds as soon as the requested index exists. Remaining pagination stays in the reader ViewModel scope. An exact saved chapter ID, when it belongs to the same novel and selected index, starts loading concurrently with navigation metadata; the foreground reader shares its result, including failures. Reader-specific catalog loading disables the detail screen's unrelated chapter-one warmup. Detail browsing retains its existing warmup behavior. No chapter URL is guessed from a chapter number.

ReaderCacheStore keeps parsed text and navigation metadata in the app cache directory, using source/canonical-URL keys, atomic file replacement, and off-main-thread IO. Storage is capped at 12 MiB and 96 entries, with per-entry limits of 512 KiB for text and 2 MiB for navigation; a small eight-entry memory cache avoids repeated disk reads. Cache corruption, eviction, or write failures fall back to the normal source path. Manual chapter refresh bypasses both cache layers and updates them after success. Existing user progress and bookmark storage are separate. Android may clear disposable app cache, so first uncached reads still depend on source/network speed.

Cached navigation is emitted before background refresh. A partial refresh never overwrites a larger persisted snapshot; explicit chapter-list refresh can rebuild it. ChapterStartupTest covers first-page chapter 83, later-page startup, new repository/process state, cache refresh bypass, corruption/eviction, and offline partial pagination.


## Discover rating resolution

Phoenix ranking cards and FWN release rows can omit a rating even when the novel detail page has one. The badge dash is a missing-data representation; it does not initiate a fetch. Discover now registers actual viewport visibility (both the vertical section and horizontal card must be visible) and resolves missing scores through the existing source detail repository. Duplicate cards share one request and all matching section instances receive the score. Two concurrent lookups, existing source rate limits/detail caches, a bounded failure cooldown and cancellation when the last card leaves the viewport prevent a request per catalog entry or repeated retries on recomposition. A 650ms dwell skips quick scrolls and Lazy prefetch items never register as visible. Leaving Discover, backgrounding the app, and changing source cancel queued and active lookups. Cancellation keeps the 60s attempt cooldown once HTTP has started; returning to a card does not repeatedly restart downloads. Foreground resume rechecks registered visible cards. Feed rendering never waits for these lookups. Carousel auto-advance runs only while its section is visible and the app lifecycle is started. Phoenix and FWN HTTP calls use a cancellable asynchronous adapter through body consumption; every response closes, including rejected status codes, whose bodies are skipped.

Same-source canonical identity is used for cache/feed reuse. A known score elsewhere in the feed or local repository is reused before requesting details. Positive inline scores are retained; missing records do not erase already resolved scores during feed refresh. Refresh explicitly resets source circuit breakers.

RatingParser reads JSON-LD aggregate ratings, graphs/arrays and declared rating scales. Both source parsers also accept explicit widget attributes and visible scores. Phoenix search and generic novel construction no longer manufacture 4.7/4.5 defaults for absent metadata. Unknown/unrated/blocked responses remain a dash. A 403/Cloudflare rejection cannot be repaired by inventing a score; the source can be retried with Refresh after access is available.

DiscoverRatingTest exercises both sources, duplicate sections, bounded visibility-based requests, cancellation, failed-lookup cooldowns, refresh retention, widget/JSON-LD fallbacks and cross-source isolation. Live Phoenix ranking access returned HTTP 403 in this environment; fixtures verify its parsing paths, not live device availability.
