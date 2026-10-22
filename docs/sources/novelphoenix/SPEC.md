# NovelPhoenix Source Extraction Specification (Verified)

- **Source Name:** NovelPhoenix
- **Base URL:** `https://novelphoenix.com`
- **Application Engine:** Custom Laravel Framework (Confirmed via `XSRF-TOKEN`, `novelphoenixcom_session`, `csrf-token`, and Blade templates)
- **Status:** Stage 1 Verified (All data backed by saved fixtures under `docs/sources/novelphoenix/fixtures/`)

---

## 1. Discovery & Protocols

| Endpoint / Feature | Exists? | Details / Findings |
| :--- | :--- | :--- |
| `/robots.txt` | **Yes** | Allows all crawlers (`User-agent: *`, `Allow: /`), reserves content signals (`search=yes`, `ai-train=no`), explicitly disallows AI training agents (GPTBot, ClaudeBot, etc.), points to sitemap `https://novelphoenix.com/sitemap.xml`. |
| `/sitemap.xml` | **Declared** | Declared in `robots.txt`. Returns HTTP 403 to bare requests (Cloudflare protection). |
| RSS / Atom feeds | **No** | "not available" (No RSS or Atom feed links present in page `<head>`). |
| WordPress REST (`/wp-json/`) | **No** | Site is built on Laravel, not WordPress. `/wp-json/` does not exist. |
| Structured Data (JSON-LD) | **Yes** | Present on detail pages: (1) `Organization`, (2) `AggregateRating` (`ratingValue`, `ratingCount`, `bestRating`, `worstRating`), (3) `BreadcrumbList`. |
| OpenGraph / Meta Tags | **Yes** | Extensive: `og:title`, `og:description`, `og:image`, `og:url`, `og:site_name`, `twitter:card`, `twitter:title`, `twitter:image`, `csrf-token`. |
| Terms of Service | **Yes** | `https://novelphoenix.com/page/terms-of-service`. States: content is hosted on 3rd party services; search engines may index without archiving; forbids automated scraping/crawling for commercial solicitation or artificial stats inflation. Personal reading apps must use polite requests (<= 1 req/s) with local caching. |
| Live Search AJAX API | **Yes** | `GET https://novelphoenix.com/ajax/searchLive?keyword={query}&type={title\|author\|both}` returns clean JSON with novel slug, title, image, rank, and total chapters. |
| HTML Chapter Paging | **Yes** | `GET https://novelphoenix.com/novel/{slug}/chapters?page={page}` (100 chapters per batch). |

---

## 2. Page Types & URL Patterns

### A. Novel Detail Page
- **Pattern:** `https://novelphoenix.com/novel/{slug}`
- **Method:** `GET`
- **Saved Fixtures:**
  - `docs/sources/novelphoenix/fixtures/detail_genetic_ascension.html`
  - `docs/sources/novelphoenix/fixtures/detail_shadow_slave.html`
- **Description:** Complete novel metadata including title, author, rating, ranking, cover, genres, tags, summary, view counters, bookmark counts, and link to chapters.

### B. Chapter List Page
- **Pattern:** `https://novelphoenix.com/novel/{slug}/chapters?page={page}`
- **Method:** `GET`
- **Pagination:** Paged in 100-chapter chunks (`page=1` for 1-100, `page=2` for 101-200, etc.). Dropdown `<select>` lists all ranges.
- **Saved Fixtures:**
  - `docs/sources/novelphoenix/fixtures/chapters_genetic_ascension.html` (2451 chapters across 25 pages)
  - `docs/sources/novelphoenix/fixtures/chapters_shadow_slave.html` (3221 chapters across 33 pages)

### C. Chapter Reader Page
- **Pattern:** `https://novelphoenix.com/novel/{slug}/chapter-{chapterNumber}`
- **Method:** `GET`
- **Saved Fixtures:**
  - `docs/sources/novelphoenix/fixtures/chapter_1_genetic_ascension.html`
  - `docs/sources/novelphoenix/fixtures/chapter_1_shadow_slave.html`
- **Description:** Full chapter reading canvas with title, body paragraphs, and next/prev navigation links.

### D. Live Search API
- **Pattern:** `https://novelphoenix.com/ajax/searchLive?keyword={keyword}&type={title|author|both}`
- **Method:** `GET` (Header: `X-Requested-With: XMLHttpRequest`)
- **Saved Fixture:** `docs/sources/novelphoenix/fixtures/search_live_shadow.json`
- **Response Format:** JSON:
  ```json
  {
    "data": [
      {
        "title": "Shadow Slave",
        "slug": "shadow-slave",
        "rank": 1,
        "total_chapter": 3221,
        "image": "server-1/shadow-slave.jpg"
      }
    ]
  }
  ```

### E. Full Search Page
- **Pattern:** `https://novelphoenix.com/search?keyword={keyword}&type={title|author|both}&page={page}`
- **Method:** `GET`
- **Saved Fixture:** `docs/sources/novelphoenix/fixtures/search_page_shadow.html`

### F. Browse / Genre Listing Page
- **Pattern:** `https://novelphoenix.com/genre-{genre}/sort-{sort}/status-{status}/{country}-novel?page={page}`
  - `genre`: `all`, `fantasy`, `action`, `romance`, `sci-fi`, etc.
  - `sort`: `new`, `popular`, `latest-release`
  - `status`: `all`, `ongoing`, `completed`
  - `country`: `all`, `chinese`, `japanese`, `english`
- **Method:** `GET`
- **Saved Fixture:** `docs/sources/novelphoenix/fixtures/browse_new.html`

### G. Rankings Page
- **Pattern:** `https://novelphoenix.com/ranking` (Tabs: `/ranking`, `/ranking/ratings`, `/ranking/most-read`, `/ranking/most-review`, `/ranking/most-lib`)
- **Method:** `GET`
- **Saved Fixture:** `docs/sources/novelphoenix/fixtures/ranking.html`

---

## 3. Verified Field Map

### A. Novel Details (from `detail_genetic_ascension.html`)

| Field | Source / Selector | Attribute / Extraction | Sample Real Value | Normalization |
| :--- | :--- | :--- | :--- | :--- |
| **id / slug** | `link[rel="canonical"]` or `meta[property="og:url"]` | Extract path segment after `/novel/` | `"genetic-ascension"` | Strip trailing slashes/params |
| **title** | `h1.novel-title` | `.text()` | `"Genetic Ascension"` | Trim whitespace |
| **alternative titles** | None | "not available" | `null` | N/A |
| **author** | `.author a[itemprop="author"]` or `.author a.property-item` | `.text()` | `"Awespec"` | Trim whitespace |
| **cover image URL** | `.fixed-img figure.cover img` | `attr("src")` or `meta[property="og:image"]` | `"https://novelphoenix.com/server-1/genetic-ascension.jpg"` | Resolve relative URLs with base URL |
| **synopsis** | `.summary .content.expand-wrapper p` | Combine `.text()` of paragraphs | `"The people of Earth have been summoned once again..."` | Join with newlines; drop `.expand` button |
| **status** | `.header-stats span:has(small:contains("Status")) strong` | `.text()` | `"Ongoing"` | Spelled `"Ongoing"` or `"Completed"` |
| **genres** | `.categories ul li a.property-item` | `.text()` list | `["Fantasy"]` | List of strings |
| **tags** | `.tags ul.content li a.tag` | `.text()` list | `["Action", "Dark", "Survival", "System", "Superpowers", ...]` | List of trimmed strings |
| **rating** | `script[type="application/ld+json"]` (AggregateRating) or `.rating strong.nub` | `ratingValue` or `.text()` | `3.5` | Float (scale: 0.0 - 5.0) |
| **rating count** | `script[type="application/ld+json"]` (AggregateRating) | `ratingCount` | `37` | Integer |
| **ranking position**| `.rating .rank strong` | `.text()` | `"RANK 112"` | Parse integer `112` |
| **views** | `.header-stats span:has(small:contains("Views")) strong` | `.text()` | `"2.5M"` | Retain formatted or parse |
| **bookmarks** | `.header-stats span:has(small:contains("Bookmarked")) strong` | `.text()` | `"2.8K"` | Retain formatted or parse |
| **total chapters** | `.header-stats span:has(small:contains("Chapters")) strong` | `.text()` | `2451` | Parse integer |
| **last updated** | `.chapter-latest-container .update` | `.text()` | `"Updated 16 hours ago"` | Clean relative string |
| **latest chapter** | `.chapter-latest-container .latest` | `.text()` | `"Chapter 2452 Heaven’s Answer (3) [Bonus]"` | Trim whitespace |
| **language** | `html[lang]` | `attr("lang")` | `"en"` | Lowercase string |
| **post_id** | `script` regex `post_id='(\\d+)'` or `#novel-report[report-post_id]` | attr or regex | `"6884"` | Used for AJAX comments/likes |

---

### B. Chapter List Items (from `chapters_genetic_ascension.html`)

| Field | Source / Selector | Attribute / Extraction | Sample Real Value | Normalization |
| :--- | :--- | :--- | :--- | :--- |
| **chapter number**| `span.chapter-no` | `.text()` | `"1"` | Parse float/int (`1.0f`) |
| **title** | `strong.chapter-title` | `.text()` | `"Chapter 1 Worst-Case Scenario"` | Trim whitespace |
| **URL** | `ul.chapter-list li a` | `attr("href")` | `"/novel/genetic-ascension/chapter-1"` | Resolve to `https://novelphoenix.com/novel/genetic-ascension/chapter-1` |
| **release date** | `time.chapter-update` | `.text()` | `"2 years ago"` | Trim whitespace |
| **locked / coin** | None | "not available" | `false` | All chapters on NovelPhoenix are unlocked |
| **total pages / range** | `select[aria-label="Chapter range"] option, .chaprange select option` | `option[value]` / regex `page=(\d+)` | `33` options (`"1-100"` to `"3201-3221"`) | Parse total page count from dropdown for concurrent background batch fetching |

---

### C. Chapter Content (from `chapter_1_genetic_ascension.html`)

| Field | Source / Selector | Attribute / Extraction | Sample Real Value | Normalization / Stripping |
| :--- | :--- | :--- | :--- | :--- |
| **chapter title** | `#content h4, .titles .chapter-title, .box-detail h1 .chapter-title` | `.text()` | `"Chapter 1 Worst-Case Scenario"` | Trim whitespace |
| **chapter body** | `#content p` (Fallback: `<br>`/text nodes if `<p>` absent) | List of `.text()` | `"Sylas shrugged off his lab coat..."` | Filter out empty lines, ads, and scripts |
| **prev chapter** | `a.prevchap[href], a.chnav.prev[href]` | `attr("href")` | `null` (has `.isDisabled` & `href="javascript:;"`) | Return null if disabled / javascript |
| **next chapter** | `a.nextchap[href], a.chnav.next[href]` | `attr("href")` | `"/novel/genetic-ascension/chapter-2"` | Resolve to absolute URL |
| **ad / noise removal** | Elements to strip: `.nf-ads`, `ins.pubadx-slot`, `ins`, `iframe`, `#frame`, `script`, `.sharethis-inline-share-buttons` | Remove from DOM before extracting text | Clean reading text only |

---

### D. Listing Cards (from `browse_new.html`, `ranking.html`, & `search_page_shadow.html`)

| Field | Source / Selector | Attribute / Extraction | Sample Real Value | Normalization |
| :--- | :--- | :--- | :--- | :--- |
| **slug** | `li.novel-item a[href*='/novel/']` | Extract from `href` | `"shadow-slave"` | Path segment after `/novel/` |
| **title** | `.novel-title, h4.novel-title, h2.title a, .title a, h4, h2 a` (Fallback: `a[title]` attribute) | `.text()` or `attr("title")` | `"Shadow Slave"` | Must target leaf heading/anchor text directly, NEVER recursive `a.text()` which embeds crown rank and chapter stats |
| **cover** | `img.lazy[data-src]` | `attr("data-src")` fallback `attr("src")` | `"/server-1/shadow-slave.jpg"` | Prepend `https://novelphoenix.com` |
| **chapter count**| `.novel-stats:has(.icon-book-open)`, `.novel-stats span:contains(Chapters)` | `.text()` | `"3221 Chapters"` | Filter stats containing "Chapter" |
| **rank** | `span.badge._bl`, `.novel-stats:has(.icon-crown)`, `.novel-stats strong:contains(Rank)`, `span.rank-counter` | `.text()` | `"R 1"` or `"Rank 1"` | Parse integer |
| **status** | `span.status` | `.text()` | `"Ongoing"` | Trim |

---

## 4. Hard Parts & Edge Cases

1. **Chapter Pagination by 100s**:
   - Long-running novels like *Shadow Slave* (3221 chapters) and *Genetic Ascension* (2451 chapters) paginate chapters at `/novel/{slug}/chapters?page={N}`.
   - Page 1 contains chapters 1–100, Page 2 contains 101–200, etc.
   - Total pages can be discovered either from `<select aria-label="Chapter range"> option:last-child` or calculated as `ceil(totalChapters / 100)`.
2. **Lazy-Loaded Covers**:
   - `<img>` tags use a transparent 1x1 gif in `src` (`data:image/gif;base64,...`) and the real image URL in `data-src` (`/server-1/{slug}.jpg`).
   - Parsers must check `data-src` first.
3. **Root-Relative URLs**:
   - All assets and links start with `/` (e.g., `/server-1/...`, `/novel/...`).
   - All URLs must be resolved against `https://novelphoenix.com`.
4. **Ad Injection Inside Chapter Text**:
4. **Ad Injections in Chapter Content**:
   - The site uses JavaScript to inject `.nf-ads` into `#content` after paragraphs 13, 26, 39, 52.
   - When parsing `#content`, the parser must explicitly discard all `.nf-ads`, `ins`, and `iframe` nodes.
5. **Fast Live Search vs Full Search**:
   - `GET /ajax/searchLive?keyword={query}&type=title` provides fast, clean JSON without requiring full DOM parsing.
   - If fallback is needed, `GET /search?keyword={query}&type=title` provides the standard HTML listing.
6. **Chapter Identity vs Numbering Gaps**:
   - Chapter numbers in titles are labels, not array indexes (e.g. Genetic Ascension has 2451 chapters, but latest title is "Chapter 2452 ... [Bonus]").
   - The chapter's URL path (`/novel/{slug}/chapter-{id}`) serves as its unique identity.
   - Pagination pages through `/novel/{slug}/chapters?page=N` starting at `page=1` until an empty list returns. The `<select>` element serves only as a hint.
7. **Relative Dates & Approximation**:
   - Relative strings ("2 years ago", "Updated 16 hours ago") are stored verbatim as `rawDate`, with an approximate `fetchTimestamp` calculated at fetch time. Marked display-only and not used for strict sorting.
8. **Counts & Formatting**:
   - `views` ("2.5M") and `bookmarks` ("2.8K") are stored as raw strings along with parsed approximate `Long` values for formatting.
9. **Rank**:
   - Detail page "RANK n" is parsed into `rank: Int`. Listing badge "R n" is treated as unverified and not conflated.
10. **Robust URL Resolver**:
    - Handles absolute URLs (`https://...`), leading-slash paths (`/server-1/...`), bare relative paths (`server-1/...`), and ignores `data:` image placeholders.
11. **Cloudflare & Blocked Responses**:
    - 403, 429, and Cloudflare challenge pages (containing `<title>Attention Required! | Cloudflare</title>` or `cf-mitigated`) trigger a typed `SourceBlockedError`.

---

## 5. Verification Fixtures Table

| Fixture File | Type | Key Verified Values |
| :--- | :--- | :--- |
| `detail_genetic_ascension.html` | Novel Detail | Title: "Genetic Ascension", Author: "Awespec", Rank: 112, Rating: 3.5 (37 reviews), Views: 2.5M, Bookmarks: 2.8K, Chapters: 2451, Status: "Ongoing", Genres: ["Fantasy"], Tags: 9 tags |
| `detail_shadow_slave.html` | Novel Detail | Title: "Shadow Slave", Author: "Guiltythree", Rank: 1, Chapters: 3221, Status: "Ongoing", Views: 15.2M |
| `detail_lord_of_the_mysteries_completed.html` | Novel Detail (Completed) | Title: "Lord of the Mysteries", Author: "Cuttlefish That Loves Diving", Status: "Completed" (class `.completed`), Chapters: 1432, Views: 16.8M, Bookmarks: 18.5K |
| `detail_kill_the_sun_no_tags.html` | Novel Detail (No Tags) | Title: "Kill the Sun", Author: "Unknown/A", Tags: empty list `[]`, Chapters: 992 |
| `chapters_genetic_ascension.html` | Chapter List (P1) | 100 items (Ch 1 "Worst-Case Scenario" to Ch 100 "Exploded"), 25 total range pages |
| `chapters_shadow_slave.html` | Chapter List (P1) | 100 items (Ch 1 to Ch 100) |
| `chapters_shadow_slave_page_17.html` | Chapter List (Middle P17) | 100 items (Ch 1601 to Ch 1700) |
| `chapters_shadow_slave_page_33.html` | Chapter List (Last P33) | 21 items (Ch 3201 to Ch 3221, final page) |
| `chapter_1_genetic_ascension.html`| Chapter Reader (Ch 1) | Title: "Chapter 1 Worst-Case Scenario", Next: "/novel/genetic-ascension/chapter-2", Prev: null (`isDisabled`), Body: clean paragraphs |
| `chapter_2452_bonus_genetic_ascension.html` | Chapter Reader (Bonus) | Title: "Chapter 2452 Heaven’s Answer (3) [Bonus]", Next: null / latest, Prev: "/novel/genetic-ascension/chapter-2451" |
| `chapter_1432_last_lotm.html` | Chapter Reader (Final) | Title: "Chapter 1432 For the Next Generation", Next: null (`isDisabled`), Prev: "/novel/lord-of-the-mysteries/chapter-1431" |
| `search_live_shadow.json` | Live Search API | 6 results: Shadow Slave (Rank 1, 3221 ch), My Living Shadow System (Rank 27, 1270 ch), etc. |
| `search_live_short_keyword.json` | Live Search API (< 3 char) | Results for "ab" |
| `search_live_zero_results.json` | Live Search API (0 res) | Empty results: `{"data":[]}` |
| `response_404.html` | HTTP 404 Page | Title: "Page Not Found 404" |
| `response_403_cloudflare.html` | Cloudflare 403 Challenge | Title: "Attention Required! | Cloudflare" |
| `ranking.html` | Rankings Feed | Rank 1: Shadow Slave, Rank 2: Lord of the Mysteries |
| `browse_new.html` | Browse Feed | 24 novel items with title, cover, chapter count, badges |

---

## 6. Endpoints Summary Table

| Endpoint Pattern | Method | Key Parameters | Response Type | Primary Fields Provided | Known Failure Modes |
| :--- | :---: | :--- | :--- :--- | :--- | :--- |
| `/novel/{slug}` | `GET` | `slug` | HTML | Complete novel details, author, summary, status, rank, views | 404 if slug misspelled |
| `/novel/{slug}/chapters` | `GET` | `slug`, `page` (optional, default 1) | HTML | Batch of 100 chapters (title, number, url, date) | Page out of bounds returns empty list |
| `/novel/{slug}/chapter-{no}` | `GET` | `slug`, `no` (or slug path) | HTML | Chapter content paragraphs, prev/next links | 404 if chapter does not exist |
| `/ajax/searchLive` | `GET` | `keyword`, `type` (`title`\|`author`\|`both`) | JSON | Slug, title, rank, total_chapter, image | Returns empty array if no match or keyword < 3 chars |
| `/search` | `GET` | `keyword`, `type`, `page` | HTML | Full paginated search results HTML | None |
| `/ranking` | `GET` | None (or subpaths `/ratings`, `/most-read`) | HTML | Curated ranked novel cards | None |
| `/genre-{g}/sort-{s}/status-{st}/all-novel` | `GET` | `page` | HTML | Filtered feed of novel cards | None |


---

## What We Require from the User to Finalize Extraction:

1. **Chapter Content View Page Source**:
   - The user mentioned: *"after you can fetch properly this content then i'll give source code from view page surce of chapter."*
   - We already saved a live real response of `https://novelphoenix.com/novel/genetic-ascension/chapter-1`.
   - Please confirm if your saved view page source matches `docs/sources/novelphoenix/fixtures/chapter_1_genetic_ascension.html` or if you have a chapter with images, tables, or author notes you would like included as a fixture.
2. **Approval to Proceed to Stage 2**:
   - Upon your approval, we will create the folder structure:
     - `app/src/main/java/com/novelnest/app/data/remote/novel/novelphoenix/`
     - `app/src/main/java/com/novelnest/app/data/remote/novel/freewebnovel/`
     - `app/src/main/java/com/novelnest/app/data/remote/manhwa/asura/`
   - Implement the pure parser functions and NovelPhoenix source repository with unit tests against the saved fixtures.
