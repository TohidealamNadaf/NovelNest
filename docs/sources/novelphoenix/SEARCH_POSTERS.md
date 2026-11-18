# NovelPhoenix Search posters

Updated 16 November 2026.

Search previously preferred the lightweight /ajax/searchLive autocomplete JSON.
It accepted those records even with missing/incorrect cover data, and only fell
back to HTML when the HTTP body was absent. A successful autocomplete response
therefore prevented the normal listing parser from supplying website poster URLs.
Its manual JSON extraction also does not decode every JSON string escape.

NovelPhoenixSource.search now requests /search?keyword=...&type=title and reuses
parseListingHtml, which already serves Browse/Popular and reads data-src before
src. One source request supplies all result cards and their real cover URLs.
There are no per-result detail requests, image probes, retry loops, cache clears,
new dependencies, or shared image-loader changes. Keyword parameters are encoded
with HttpUrl. Canonical novel IDs and detail/reader routes are retained, as are
the existing transport cooldown, HTTP cache, source rate limiter and cancellation.
FreeWebNovel, Asura and the card UI are unaffected.

The AJAX JSON parser remains available for its documented historical contract;
its payload is no longer used to populate production Search poster cards.

## Verification boundary

The focused offline run passed 55 tests with zero failures, errors or skips, and
assembleDebug succeeded. This includes both novel source route contracts, Asura
parser checks, Search history/source-switch checks and the new poster regressions.

Offline regression tests check Search/Detail cover consistency against the saved
source HTML, lazy-loaded covers, identity, rating, rank, query encoding, zero
results, blank queries and block cooldown without extra requests. Existing route
contracts check search-to-detail, bookmarks, chapter lists and reader navigation.

The host could not fetch current NovelPhoenix search data during this investigation
(connection timeouts; the web fetch returned HTTP 403). The emulator also reported
DNS failures and displayed cached search records. A currently changed API field or
specific live image HTTP rejection could not be confirmed. The implementation
removes the autocomplete dependency; live poster recovery still requires checking
the updated APK on a device with working source access.
