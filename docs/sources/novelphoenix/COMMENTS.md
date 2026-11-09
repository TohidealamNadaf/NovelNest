# NovelPhoenix chapter comments: researched fetch contract

Verified on **4 November 2026** against the public site, anonymously. This document
records research and an implementation design. Chapter comments are **not yet
implemented** in the Android application.

## Verified result and evidence

Anonymous chapter-comment reading works through a same-origin GET endpoint. The
response is JSON containing rendered HTML and cursor metadata, rather than a JSON
array of comment objects. No login cookie or CSRF token was sent in the verified
requests. A successful anonymous response does not establish a permanent API or
promise access for every chapter, account, region or network.

Primary sources inspected:

- [Shadow Slave chapter 1](https://novelphoenix.com/novel/shadow-slave/chapter-1).
- [Current comment client](https://novelphoenix.com/frontend/js/comment.min.js?ver=2.01).
- [Current shared client](https://novelphoenix.com/frontend/js/app.min.js?ver=2.01).
- [Current chapter client](https://novelphoenix.com/frontend/js/chapter.min.js?ver=2.01).
- [Verified anonymous first-page request](https://novelphoenix.com/comment/show?post_id=2196&chapter_id=58592219&order_by=newest&cursor=).

[The observation record](fixtures/comments_observations_2026-11-04.json) contains
response structure, HTTP results, counts and public-script hashes. It omits user
comment text, usernames, cursor tokens and session material. Full temporary
responses were inspected locally under the ignored build/diagnostics directory.
No public comments or account actions were submitted.

The web research fetcher received HTTP 403 for the chapter, while direct anonymous
HTTP requests from this workstation returned HTTP 200. These are observations from
different clients; anonymous access must still handle a genuine challenge or block.

## How the website loads comments

1. The initial chapter HTML contains an empty element with id chapter-comments.
   Scraping that element from the initial HTML will therefore return no comments.
2. The page exposes numeric post_id and chapter_id variables in an inline script.
3. The chapter client calls initCommentLoader with offset 0. The shared client
   loads the comments script and stylesheet after a scroll, and calls ajaxComment
   when the comments region reaches the viewport.
4. The comment client creates the comments interface, then calls showComment.
   The ordinary entry path requests Newest with an empty cursor.
5. Load More submits the returned cursor. Changing sort resets that cursor and
   replaces the displayed thread list.

A native client can perform the documented GET directly and parse its HTML with
Jsoup. It does not need to execute the site's scripts, editor, ads or analytics.

## Chapter identity: do not use the chapter number

| Value | Observed value | Meaning |
| --- | --- | --- |
| Chapter URL suffix | chapter-1 | Reader URL label, not the database ID |
| post_id | 2196 | Numeric novel database ID |
| chapter_id | 58592219 | Numeric chapter database ID used by comments |

Both numeric IDs were present as parseInt assignments in the live chapter HTML.
They must be extracted from that chapter's own script data, validated as positive
digit strings, and associated with its canonical source URL. Do not guess IDs from
chapter indexes, titles, NovelNest IDs or neighboring chapters, and do not hardcode
the observed Shadow Slave values for other pages. Parse narrowly without evaluating
JavaScript. Keep fallbacks for quoted numeric assignments if later observed.

The existing PhoenixChapterContent.id is derived from the chapter URL suffix.
Neither it nor the shared Chapter model currently retains the numeric comment IDs.

## Request contract

**GET https://novelphoenix.com/comment/show**

| Query field | Meaning | First page | Following pages |
| --- | --- | --- | --- |
| post_id | Numeric novel ID from the chapter HTML | Required | Same ID |
| chapter_id | Numeric chapter ID from the chapter HTML | Required | Same ID |
| order_by | newest, oldest or mostliked | Selected sort | Same sort |
| cursor | Opaque token issued by this endpoint | Empty | Previous next_cursor |

Verified requests supplied Accept: application/json, X-Requested-With:
XMLHttpRequest, and Referer set to the canonical chapter URL. No cookie, bearer
credential or CSRF token was sent. Whether each supplied header is individually
necessary was not tested. Build query parameters using HttpUrl/URLSearchParams;
never concatenate or decode/reconstruct a cursor token.

No page number, page size or total count was exposed by this client contract.
Do not substitute the separate chapter-list page=N mechanism.

## Response and pagination

| JSON property | Type | Handling |
| --- | --- | --- |
| html | String | Parse as an HTML fragment containing top-level li elements |
| next_cursor | String observed | Keep opaque; handle null/empty defensively |
| has_more_pages | Boolean | More pages require both true and a nonempty cursor |

The client explicitly checks both pagination fields. It does not derive the next
page from the number of returned comments. The terminal response itself was not
visited in this bounded research session, so its exact null/string convention is
still unverified.

| Probe | HTTP | Root comments | All comment items | Embedded replies |
| --- | --- | --- | --- | --- |
| Newest, first page | 200 | 10 | 18 | 8 |
| Newest, next cursor | 200 | 10 | 12 | 2 |
| Oldest, first page | 200 | 10 | 51 | 41 |
| Most Liked, first page | 200 | 10 | 58 | 48 |

The two consecutive Newest pages had no overlapping root IDs during the probe.
Ten roots is an observation, not a guaranteed page-size contract. Replies can make
a response much larger than the root count suggests; the sampled HTML ranged from
about 23K to 112K characters. Deduplicate by comment ID when appending and stop on a
repeated cursor to prevent a malformed pagination loop. Inserts/deletions can occur
between requests; reset pagination when refreshing or changing chapter/sort.

## HTML field map and reply structure

Wrap the fragment in a synthetic ul solely for parsing. Iterate that wrapper's
**direct li children** to identify roots; selecting every li would also collect
replies and presentation controls. For each root li, use its direct .comment-item
child as the comment record.

| Field | Selector/attribute within that comment item | Notes |
| --- | --- | --- |
| Comment ID | data-comid; id=lnw-comment-{id} | Preserve as a string; validate consistency |
| Author display name | .comment-header .username | Text only; tolerate missing/deleted author |
| Profile URL | .user-info .head-items a[href^='/user/'] | Optional, resolve against site origin |
| Avatar | .user-avatar img.avatar[src] | Observed on img.novelphoenix.com; optional |
| User tier | .user-info .tier | Display text, not an authorization claim |
| Relative post time | .post-date | Sample had class only, no datetime attribute |
| Comment content | .comment-body .comment-text | HTML with paragraphs, links and mentions |
| Spoiler flag | .comment-text[data-spoiler] | Observed 0 and 1; default hidden when flagged |
| Likes | .like-group label span | Parse defensively; missing is unknown, not invented |
| Dislikes | .dislike-group label span | Preserve separately from likes |
| Reply-to reference | .parent-link a[href^='#lnw-comment-'] | Refers to another comment ID |
| Optional permalink override | data-comment-url | Used by client when present; not observed in sampled items |

Replies are nested in .reply-comments > ul > li containers associated with the
parent li, usually outside its .comment-item div. Nested replies were observed.
A li.show_replies element is a presentation control, not a comment. Its click
handler in the current shared script toggles expansion locally; it does not request
another reply endpoint. The sampled endpoint already includes the displayed reply
HTML. This does not prove that the server returns every historical reply.

Preserve server DOM order. Capture explicit reply-to IDs where available and use
DOM ancestry as a fallback grouping signal. Scope field selectors to each record
so a parent never inherits a reply's author, body or vote count. Represent threads
as IDs and relationships or a bounded flattened structure, so deeply nested HTML
cannot cause unbounded recursive UI work.

Keep spoiler state separate from text; do not reveal it during parsing. Use safe
text or a restricted rich-text representation. Resolve links against the chapter
URL, retain paragraph breaks, and allow only appropriate URL schemes. Strip scripts,
iframes, forms, event handlers and interaction toolbars from rendered body content.
Do not execute or embed the returned fragment as a live page.

## Other routes seen in the current script

These were identified by script inspection only; they were not invoked:

| Method | Route | Purpose / boundary |
| --- | --- | --- |
| GET | /comment/showFromInbox | Focused comment context using comment_id, type, userid; not the normal page loader |
| GET | /comment/voters | Vote-user list using comment_id, status_like, page; unnecessary for chapter comments |
| POST | /account/comment/add | Write a comment |
| POST | /account/comment/reply | Write a reply |
| PUT | /account/comment/edit | Edit a comment |
| DELETE | /account/comment/delete | Delete a comment |
| POST | /account/comment/like or /account/comment/dislike | Change a vote |

Write paths submit CSRF material in the site's client and need a separate account
integration design and explicit authorization. Anonymous reading does not establish
permission to mutate an account. The comment permalink client uses comid in the
chapter query string and may use data-comment-url as its base.

## Integration design for the current repository

1. **Source models and selectors:** add PhoenixCommentContext, PhoenixCommentSort,
   PhoenixComment, and PhoenixCommentPage to NovelPhoenixModels.kt (or a dedicated
   source comment model file). Keep selectors in NovelPhoenixSelectors.kt and
   pure context/fragment parsing in NovelPhoenixParser.kt or a focused comment
   parser. Comments should not become reading paragraphs or alter chapter IDs.
2. **Retain context during the existing chapter fetch:** extract the numeric IDs
   alongside text in parseChapterContent and retain them by normalized chapter URL.
   fetchChapterContent already serializes/cache-hits per URL. Comment-ID extraction
   failure must not fail otherwise valid chapter reading. The current disk reader
   cache stores text/navigation only; when only that cache survives a restart,
   resolve missing comment context once on demand from the chapter page. Alternatively
   version a dedicated disposable context cache; do not add a Room migration solely
   for source IDs or attach comments to bookmark records.
3. **Source-owned HTTP:** add a typed getChapterComments operation behind the source
   abstraction, then expose it through NovelRepository. Call the existing
   executePoliteGet with isAjax=true. Share NovelPhoenix's host limiter and cooldown
   with chapter/search/discovery traffic. A separate client/limiter would allow
   comment scrolling to compete with chapter loading and weaken the recent fix.
4. **Demand and priority:** request the first comment page only when the user opens
   comments, and the next only on an explicit pagination/scroll threshold. Do not
   prefetch comments for every chapter or poll for updates. Cancel work and ignore
   stale results when chapter/sort changes; deduplicate matching in-flight requests.
5. **Cache:** key by source, normalized chapter URL, numeric context, sort and cursor.
   Use a short first-page TTL, a byte-bounded LRU, and a bounded active-page window.
   Keep comments separate from chapter text and durable notes/progress. A 2-minute
   first-page TTL and a 4-MiB memory budget are starting design choices, not website
   requirements. Impose an explicit response-size limit before HTML parsing and
   report a size failure rather than silently dropping part of a thread.
6. **Reader state:** use separate loading/empty/error/loaded/appending states for
   comments. A comment error must leave the chapter and reading position usable;
   an append failure must retain loaded roots and expose retry for that cursor.
   Cap visual nesting, preserve relationships, and retain spoiler reveal state by
   comment ID. UI work is outside this research task.

No new permission, dependency or persistent schema is inherently required for the
read-only first version: INTERNET, OkHttp, Jsoup, JSON parsing, MutexPool and the
existing cooldown are already present. A bounded HTTP reader would need extending
before enforcing the proposed comment-response limit: awaitTextResponse currently
reads successful text bodies in full.

Relevant current code:

- [Source networking/cache](../../../app/src/main/java/com/novelnest/app/data/remote/novelphoenix/NovelPhoenixSource.kt).
- [Source parser](../../../app/src/main/java/com/novelnest/app/data/remote/novelphoenix/NovelPhoenixParser.kt).
- [Source models](../../../app/src/main/java/com/novelnest/app/data/remote/novelphoenix/NovelPhoenixModels.kt).
- [Selector catalog](../../../app/src/main/java/com/novelnest/app/data/remote/novelphoenix/NovelPhoenixSelectors.kt).
- [Shared text HTTP response](../../../app/src/main/java/com/novelnest/app/data/remote/CancellableHttp.kt).
- [Host cooldown](../../../app/src/main/java/com/novelnest/app/data/remote/network/RateLimiter.kt).
- [Repository interface](../../../app/src/main/java/com/novelnest/app/data/repository/NovelRepository.kt).
- [Disposable reader cache](../../../app/src/main/java/com/novelnest/app/data/cache/ReaderCacheStore.kt).

## Verification to perform during implementation

- Offline parser cases: valid/missing numeric context, mixed script assignments,
  roots with nested replies, duplicate text, deleted users, empty counts, HTML
  entities, paragraph/mention preservation and spoiler flags.
- Pagination cases: each sort, terminal cursor, empty thread, repeated cursor,
  duplicate IDs across pages, changing sort mid-request and disappearing comments.
- Transport cases: anonymous success, 403/429, header/body challenge responses,
  invalid JSON, malformed fragments, cancellation, response-size limits and shared
  cooldown behavior. Cached chapter reading must remain independent.
- Reader cases: open/close comments rapidly, change chapter while loading, rotate,
  background/return, append failure and network loss after cached content.

The research made bounded anonymous read requests and parsed the four saved
responses with the project's existing Jsoup 1.18.3. No Android feature, tests or UI
were implemented. An empty-comments response, terminal cursor, other chapter/novel
pairs, and authenticated operations remain unverified. The site may change this
undocumented contract; recheck scripts and structural observations before release.
