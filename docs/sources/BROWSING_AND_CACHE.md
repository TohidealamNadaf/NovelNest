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

The reader uses the navigation entry SavedStateHandle for its current chapter and scroll position. Scroll restoration waits for paragraphs and ignores outgoing chapter callbacks. A small SharedPreferences reader session retains the active novel/chapter when the device discards normal task state. Navigating away clears that checkpoint; backgrounding the reader retains it. Normal resumes do not trigger a Home navigation or a forced reload. This adds recovery for activity/task recreation; the original device background event could not be reproduced without a connected device.

The synopsis uses pressed/focused surface feedback clipped to its rounded card instead of an expanding circular ripple; content expansion remains animated.

ReadingAndIdentityTest adds regressions for distinct read counts, startup/write ordering, canonical search identity, nested listing markup, valid empty results, reader SavedStateHandle recreation and durable session clearing. These are JVM tests; actual Android task recreation, scrolling and touch appearance still require device verification.


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
