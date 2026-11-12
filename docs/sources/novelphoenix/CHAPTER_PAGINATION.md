# NovelPhoenix chapter pagination

## Live observations: 2026-11-11

Normal anonymous HTTPS GETs returned HTTP 200 for Affinity Chaos chapter-list pages
1, 19, 20 and 23, in roughly 0.4-0.8 seconds each from the development host. Requests
were paced at least one second apart; no challenge solving, cookies or account
access were used. The web search fetcher itself returned 403, so host/device/network
access can differ. No claim is made about the exact historical response on the phone.

- Page 1: 1-100, 100 entries.
- Page 19: 1801-1900, 100 entries.
- Page 20: 1901-2000, 100 entries.
- Page 23: 2201-2224, 24 entries.
- Range selectors advertise all 23 pages, with a selected option identifying the
  returned page. Some option URLs use HTTP; the app constructs its own HTTPS URL.

Fixtures retain only the public range selector and chapter list. Full transient HTML
is kept only under ignored build/diagnostics/phoenix-pagination for local inspection.

## Causes found in the old implementation

1. Ordered progressive emission stopped at the first missing page. If page 20
   stalled/failed, the user saw 1900 even if pages 21-23 had already returned.
2. Only an entirely successful catalog was cached at the source. A retry or cancelled
   detail request discarded successful page batches and restarted page 1 onward.
3. Progressive and full-list callers used independent pagination implementations,
   allowing duplicate requests for the same novel during detail/reader transitions.
4. Only per-read/connect timeouts were configured; there was no overall deadline on
   a chapter-list HTTP call. A response that kept trickling could remain active.
5. The parser reported the requested page as current without inspecting the selected
   range. Incorrect or truncated successful HTML could therefore enter the catalog.
6. Detail state marked chapter loading finished after the first successful batch,
   although background pagination was still running.

There is no hardcoded 1900 limit and no bookmark-dependent fetching branch. These
code gaps reproduce the reported symptom; historical server blocks are unconfirmed.

## Current design

NovelPhoenixChapterLoader is the single source-level loader for progressive detail,
reader and complete-list requests. A per-novel bounded mutex pool prevents concurrent
runs for the same canonical slug. Waiting callers can still receive repository/disk
snapshots through the existing repository layer.

A fresh full catalog returns immediately. Otherwise emit only validated contiguous
pages in chronological order. Three bounded workers fetch missing pages; unchanged
prefixes are not emitted repeatedly. Never skip a failed range or change reader
indices to expose later pages early. A visible error identifies missing pages and
keeps already displayed chapters.

A retry of an incomplete session revalidates page 1 and reuses successful, unexpired
pages when the catalog layout still matches. New appended chapters invalidate only
the changed tail. A completed explicit Sync starts a fresh generation to revalidate
all pages; an expired or incompatible session also starts anew. Cancellation keeps
successfully parsed page batches, including batches completed before their ordered
UI emission. No cancelled/failed load is marked complete.

Retention is bounded and in memory: eight session headers, 128 page entries globally,
four complete lists of at most 5000 chapters each; TTL is 15 minutes using a monotonic
clock. Larger books still load but do not occupy the complete-list cache. Old
unreachable generation entries are evicted by the fixed LRU bound. There is no
application-wide pagination coroutine or timer retained by a screen.

Each chapter-list HTTP attempt has a 15-second overall deadline and at most two
attempts for transient network/5xx failures. Existing one-request-per-second host
pacing applies across all NovelPhoenix operations. A 23-page cold catalog therefore
still normally needs around 23 seconds plus competing requests/network time; this is
intentional pacing. No claim of instant complete catalogs or block-free access.

403/429/challenges preserve the existing shared cooldown and Retry-After handling,
stop queued work immediately and cancel active sibling requests as the failure
propagates. They are never automatically retried. Detail error text preserves the
cooldown duration. The existing Sync indicator remains active until the flow ends.

## Validation

The shared parser reads actual selected page and advertised range bounds. Batches
must be nonempty, unique, match the requested page and, when available, contain the
advertised number of entries. No fixed 100-page-size calculation is used for loading.
Overlapping pages cannot form a complete catalog or extend the displayed prefix
through their overlap. Layout selectors remain in NovelPhoenixSelectors.

Focused offline checks cover page-20 failure/resumption to 2224, normal retry,
cancellation, concurrent collectors, wrong-page HTML, truncated batches, overlapping
pages, cooldown cancellation, appended chapters, TTL expiry, live HTML fixtures,
bookmarked/unbookmarked detail loading, source routing and existing reader startup.
Live network smoke tests remain ignored in the offline suite. The phone's exact
historical request/error sequence and latency have not been captured.

Verification run: 88 selected tests, 86 passed, two live-network smoke tests skipped;
assembleDebug succeeded. The simulated page-20 outage recovered to 2224 chapters
with two retry requests (page 1 and page 20), rather than 23.

## Boundaries

Page checkpoints survive cancellation/retry while the source instance is alive.
They are not durable downloads. The existing repository still persists its useful
contiguous catalog snapshot for offline display; a new process revalidates live
pages. LRU eviction and TTL expiry may require additional requests. Bookmarks, reading
progress, FreeWebNovel fetching, manhwa fetching and database schema are not changed
by this fix.

Public endpoints inspected:
- https://novelphoenix.com/novel/affinity-chaos/chapters?page=1
- https://novelphoenix.com/novel/affinity-chaos/chapters?page=19
- https://novelphoenix.com/novel/affinity-chaos/chapters?page=20
- https://novelphoenix.com/novel/affinity-chaos/chapters?page=23
