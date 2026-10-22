# FreeWebNovel source extraction specification

Verified against public responses from **https://freewebnovel.com** on **2026-10-22**.
Raw responses are saved in `fixtures/`. Dynamic novel counts and recommendations describe those captures, not permanent site values.

## Endpoints and protocols

| Purpose | Verified request | Response |
| --- | --- | --- |
| Homepage | `GET /home` (the root redirects here) | Recommended cards and latest chapter releases |
| Rankings | `GET /sort/most-popular` | Ranked novel cards |
| Latest releases | `GET /sort/latest-release` | Updated novel cards |
| Search | `GET /search?keyword={UTF-8 encoded query}` | Search result cards; GET was verified although the website form declares POST |
| Novel details | `GET /novel/{slug}` | Metadata, synopsis, first 40 chapter links, and pagination attributes |
| Chapter list, HTML | `GET /novel/{slug}?page={page}` | A complete detail page with the requested 40-chapter range |
| Chapter list, JSON | `GET /novel/{slug}?ajax=chapters&page={page}&pageSize=100` | JSON with chapter-link HTML and paging metadata |
| Reading | `GET /novel/{slug}/chapter-{identifier}` | Full chapter text and previous/next links |
| Website pagination implementation | `/static/freewebnovel/js/indexlist.js?v=1.6` | Public script describing the JSON request contract |

JSON requests use `X-Requested-With: XMLHttpRequest`. The implementation uses the verified 100-chapter page size, a browser User-Agent, response closing, and a shared source limiter with at least 1 second between request starts. A maximum of three attempts is used for network failures, HTTP 429, and transient server failures. HTTP 403/404 are reported immediately.

Direct requests can receive a Cloudflare verification page: an initial PowerShell request returned HTTP 403, while ordinary browser-header curl requests returned HTTP 200. A successful fixture does not guarantee future access from every device or IP. Challenges must produce a readable error, never sample novels or placeholder text. No challenge bypass, login automation, or site bookmark write is implemented. Robots/sitemap access was not verified.

## Identity and URL rules

- Every entry point uses `fwn-{slug}` and `https://freewebnovel.com/novel/{slug}`.
- Home, Discover, Search, details, bookmarks, reader startup, and chapter snapshot keys use the same canonical URL.
- Legacy `/{slug}.html` URLs normalize to `/novel/{slug}`. The five previous numeric seed IDs are retained only as identity aliases for existing saved records.
- Old search/hash IDs are replaced using the saved record's source URL. New IDs never depend on result position, title hashes, or request origin.
- Chapter IDs come from actual chapter-link paths, such as `chapter-101`. Request URLs remain absolute and source-specific; chapter titles are display text, not array indices.
- Queries, fragments, trailing slashes, and legacy URL forms do not create separate novels.
- A matching title/slug at NovelPhoenix is a different source record. Bookmark lookup and toggling must retain that distinction.
- Novel/chapter fetch URLs must belong to `freewebnovel.com` or its `www` alias. JavaScript/data URLs and foreign hosts are rejected.

## Verified field map

| Field | Selector / extraction |
| --- | --- |
| Homepage recommendations | `.home-recommendations .home-rec-item` |
| Homepage releases | `.home-release-list > li` |
| Ranked/search cards | `.col-content .ul-list1`, with `.li-row` / `.li` cards |
| Card title and novel URL | `h3.tit a[href]` or `.home-release-title[href]` |
| Cover | `img[data-src]` followed by `img[src]`; ignore empty/data image URLs |
| Card rating | `.core span` |
| Card genres | `a[href*='/genre/']` |
| Latest release chapter | `.home-release-chapter a` or another card-local chapter link |
| Canonical novel URL | `link[rel=canonical]` |
| Detail title | `.m-desc h1.tit`, with `og:novel:novel_name` fallback |
| Author | `meta[property='og:novel:author']` |
| Detail cover | `meta[property='og:image']` |
| Synopsis | `.m-desc .txt .inner p`, or inner text when no paragraphs exist |
| Rating | `.score .vote`, numeric value before `/ 5` |
| Genres | `meta[property='og:novel:genre']`, split on commas |
| Status | `meta[property='og:novel:status']` |
| Latest chapter | `meta[property='og:novel:lastest_chapter_name']` (site spelling is intentional) |
| HTML chapter list | `#idData a[href*='/chapter-']` |
| HTML pagination | `#indexListPage[data-current-page][data-page-size][data-total-page][data-total-chapters]` |
| Chapter title | `#article h4`, with `.top .chapter` fallback |
| Chapter paragraphs | `#article p`, with `<br>` fallback |
| Previous chapter | `.ul-list7 .prev a[href]`; ignore a link back to novel details |
| Next chapter | `.reader-next-item a[href]`, `.ul-list7 a[title='Read Next chapter']` |

Unknown ratings are stored as zero, not a fabricated high rating. The parser removes scripts, styles, headings, advertising containers, `ins`, and iframes from reading text. Sidebars, comments, and reader controls are outside `#article` and are not extracted.

## Chapter JSON contract

Captured example:

```json
{
  "code": 200,
  "html": "<li>...<a href=\"/novel/shadow-slave/chapter-101\">...</a></li>",
  "page": 2,
  "pageSize": 100,
  "totalPage": 33,
  "totalChapters": 3222
}
```

The HTML embedded in `html` is parsed with Jsoup after decoding JSON with the platform JSON parser. Do not infer chapter URLs by generating numeric ranges. The initial HTML dropdown repeats the same novel URL in its option values; it is not a usable list of page URLs. Use the explicit JSON metadata and website script contract instead.

Chapters are emitted as an ordered prefix after each successful page. A failed/empty/unexpected page produces a failure and retains the already displayed real chapters. A list is marked complete only after all advertised pages and chapter counts have been validated. Partial snapshots are separate from the complete-list cache. Retry can re-request pages; successful pages can be reused during a normal retry/load.

## Application integration and offline behavior

1. Home fetches homepage recommendations, latest releases, and the live ranking list. Discover displays these fetched records through the same repository pipeline.
2. Successful lists are retained in memory and Room. The new `fwn-live-*` section namespace prevents previous placeholder sections from reappearing as supposedly fetched data.
3. Without a successful prior fetch, an offline/blocked source shows an error. Hardcoded seed lists are not substituted into live FWN Home, Discover, or Search.
4. Search performs a source search even when local matches exist. Verified local records are only an offline fallback. A valid empty source result stays empty.
5. The exact clicked novel is remembered synchronously before navigation, avoiding a race with asynchronous database persistence.
6. Details and reader resolve the same canonical URL, including older `.html` bookmarks. Bookmarks store the complete novel model and survive restarts; their source is preserved.
7. Reader startup reuses progressive chapter snapshots. First-chapter warming, foreground reading, and next-chapter prefetch share the same content cache and per-URL request lock.
8. Details use one retry/error card beside the chapter list. No error banner is inserted over the poster. Failed real novels do not receive fabricated chapter rows.
9. Complete chapter lists and metadata have a 15-minute source cache. Chapter contents are held in memory; this work does not implement a persistent offline chapter downloader.

## Fixtures and verification

| File | Verified content |
| --- | --- |
| `home.html` | Actual recommendations and latest-release cards, including real image and novel paths |
| `popular.html` | Ranking cards, starting with Shadow Slave |
| `latest.html` | Latest-release listing |
| `search_shadow.html` | Search results with Shadow Slave and stable canonical IDs |
| `search_empty.html` | Valid zero-result response with `.rank-empty`; no sample fallback |
| `detail_shadow_slave.html` | Guiltythree, rating 4.6, synopsis, genres, latest chapter, and first 40 links |
| `chapters_page_2.html` | Chapter links 41–80, current page 2, 81 HTML ranges in the capture |
| `chapters_ajax_1.json` | Chapters 1–100; total 3222; 33 API pages |
| `chapters_ajax_2.json` | Chapters 101–200 with real titles and paths |
| `chapter_1_shadow_slave.html` | Chapter 1: Nightmare Begins, real paragraphs, next chapter 2, no previous chapter |
| `indexlist.js` | Site's public pagination implementation |

`FreeWebNovelTest` verifies parser fields, stable entry-point identity, legacy URLs, blocked-source behavior, progressive paging failure/retry, concurrent content requests, clicked-record handoff, and bookmark persistence/source isolation.

`FreeWebNovelLiveSmokeTest` is opt-in with `FWN_LIVE_SMOKE=1`. It checks live Home, Search, details, the first chapter range, and chapter reading without downloading every chapter of a long novel.


## Application entry points and maintenance

Home is the source-filtered bookmarked library and does not fetch discovery feeds. Discover renders the live source sections. Its Browse button (and section See All buttons) opens the category grid for the selected source, using real category and pagination links from the source. Browse requests one catalog page at a time; it does not download the entire catalog at once. All novel cards use the shared record-seeding callback before opening details; canonical IDs, bookmark identity, chapter pagination and reader routes remain unchanged.

- FreeWebNovelSelectors.kt: centralized CSS selectors for listings, details, pagination and reader navigation.
- FreeWebNovelModels.kt: chapter-page metadata and timestamped cache records.
- FreeWebNovelUtils.kt: document parsing and verification/not-found detection.
- FreeWebNovelErrors.kt: typed layout, verification, HTTP, network and incomplete-list failures.
- FreeWebNovelUrls.kt: canonical URLs and existing bookmark ID compatibility.
- FreeWebNovelParser.kt: converts source markup into domain records.
- FreeWebNovelSource.kt: requests, rate limiting, cache locks and progressive chapter loading.

Only network failures, HTTP 429 and HTTP 5xx are automatically retried. Verification, missing resources and recognized layout failures surface immediately. Incomplete chapter lists never replace the complete-list cache. Update selectors and captured fixtures together when markup changes.


### Release poster resolution

The home release table uses 40 x 53 pixel `<img>` thumbnails named `/files/article/image/{bucket}/{id}/{id}ss.jpg`; normal listing/detail covers use `{id}s.jpg`. `FreeWebNovelUtils.posterUrl` upgrades only this known, same-host image path. The parser applies it to fresh cards/details, and database/bookmark/identity normalization applies it to older cached records. Unrelated paths and external URLs stay unchanged. This avoids stretching table thumbnails into poster cards, adds no per-novel metadata requests, and gives Coil a distinct URL/cache key for the larger image without clearing the library or image cache.
