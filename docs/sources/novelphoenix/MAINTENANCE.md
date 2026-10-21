# NovelPhoenix Source Maintenance & Operations Guide

This guide describes how to maintain, test, and update the **NovelPhoenix** source in NovelNest.

---

## 1. Architecture & Package Structure

All NovelPhoenix integration logic is kept strictly isolated inside its own package under `data/remote/novelphoenix/` to avoid polluting or touching existing sources (FWN, Asura Scans):

```
app/src/main/java/com/novelnest/app/data/remote/novelphoenix/
├── NovelPhoenixModels.kt      # Domain & DTO parser models
├── NovelPhoenixErrors.kt      # Typed exceptions (SourceBlockedError, ParseError, ResourceNotFoundError)
├── NovelPhoenixSelectors.kt   # Centralized selector registry and fallback chains
├── NovelPhoenixUtils.kt       # Safe URL resolution, metric parsing, relative date estimation
├── NovelPhoenixParser.kt      # Pure parsing functions (zero network I/O, string in -> models out)
└── NovelPhoenixSource.kt      # Polite networking, rate limiting, caching, circuit breaker

app/src/test/java/com/novelnest/app/data/remote/novelphoenix/
├── NovelPhoenixParserTest.kt      # Pure offline unit tests asserting against 21 saved fixtures
└── NovelPhoenixLiveSmokeTest.kt   # On-demand live smoke test against live production servers
```

---

## 2. Selector Catalog & Fallback Chains

Selectors are managed in [NovelPhoenixSelectors.kt](file:///e:/novel-nest/app/src/main/java/com/novelnest/app/data/remote/novelphoenix/NovelPhoenixSelectors.kt). If the site updates its DOM or Blade templates, update the selectors here without altering parser logic.

### Fallback Hierarchy
Each critical metadata field defines a priority ordered list of CSS selectors. The parser tries each selector in sequence until finding non-blank content:

| Field | Primary Selector | Structural Fallback | Label-Dependent Fallback |
| :--- | :--- | :--- | :--- |
| **Title** | `h1.novel-title` | `.novel-info .main-head h1` | `meta[property=og:title]` |
| **Author** | `[itemprop=author]` | `.novel-info .author a.property-item` | `.author a` |
| **Cover** | `.fixed-img .cover img[src]` | `.glass-background img[src]` | `meta[property=og:image]` |
| **Status** | `.header-stats strong.ongoing` | `.header-stats strong.completed` | `.header-stats span:has(small:contains(Status)) strong` *(Label-dependent)* |
| **Rank** | `.novel-info .rating .rank strong` | `.rating .rank` | `""` |
| **Views** | `.header-stats span:has(small:contains(Views)) strong` *(Label-dependent)* | `.header-stats span:nth-of-type(2) strong` | `""` |
| **Bookmarks** | `.header-stats span:has(small:contains(Bookmarked)) strong` *(Label-dependent)* | `.header-stats span:nth-of-type(3) strong` | `""` |
| **Total Chapters** | `.header-stats span:has(small:contains(Chapters)) strong` *(Label-dependent)* | `.header-stats span:nth-of-type(1) strong` | `""` |
| **Chapter Reader** | `#content` | `article#chapter-article` | `""` |
| **Next Chapter** | `a.nextchap[href]` | `a.chnav.next[href]` | `""` |
| **Prev Chapter** | `a.prevchap[href]` | `a.chnav.prev[href]` | `""` |

> [!NOTE]
> Label-dependent selectors such as `:contains("Status")` and `:contains("Views")` rely on English label text in the UI. Structural fallbacks (e.g. `strong.ongoing`, `nth-of-type(2)`) are automatically tried if English labels change.

---

## 3. Error Classification

NovelPhoenix categorizes all remote operations into three explicit error types:

1. **`PhoenixException.SourceBlockedError`**:
   - Thrown when HTTP 403, 429, or a Cloudflare Turnstile/Challenge page is encountered.
   - Allows the UI to display a user-friendly message (*"Site temporarily unavailable or blocked. Please try again later."*) instead of empty content or false parse failures.
2. **`PhoenixException.ParseError`**:
   - Thrown when an expected mandatory DOM structure is missing (e.g. `#content` missing from a reader page).
   - Indicates layout or selector regression.
3. **`PhoenixException.ResourceNotFoundError`**:
   - Thrown when a novel or chapter returns a 404 response or a "Page Not Found 404" page.

---

## 4. Etiquette & Rate Limiting

To comply with NovelPhoenix's `robots.txt` and Terms of Service:
- **Rate Limiting**: `HostRateLimiter` enforces a **minimum 1,000ms delay** between consecutive requests to `novelphoenix.com`.
- **User-Agent**: A modern browser User-Agent header is sent with every request.
- **Circuit Breaker**: Trips if 3 consecutive requests return blocked responses (403, 429, Cloudflare challenge), pausing requests for 2 minutes to protect user IP reputation.
- **In-Memory Caching**: Details and chapter lists are cached for 10 minutes to minimize server load.

---

## 5. Fixture Management & Testing

### Refreshing Fixtures
To update or regenerate test fixtures politely:
```powershell
.\docs\sources\novelphoenix\refresh_fixtures.ps1
```
The script spaces out requests by 1.5 seconds and writes directly to `docs/sources/novelphoenix/fixtures/`.

### Running Tests
To run offline parser unit tests:
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.novelnest.app.data.remote.novelphoenix.NovelPhoenixParserTest"
```

To run the live smoke test against live production servers:
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.novelnest.app.data.remote.novelphoenix.NovelPhoenixLiveSmokeTest"
```

To build the full debug APK:
```powershell
.\gradlew.bat assembleDebug --no-daemon
```
