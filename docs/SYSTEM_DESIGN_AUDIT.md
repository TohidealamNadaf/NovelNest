# System design audit — 2026-10-23

## Repair update

The five findings below were implemented after the initial audit:

1. Partial catalog refreshes merge by chapter ID without removing known entries.
   Only normal, successful completion with a nonempty validated list reconciles
   removals. Failures, thrown source errors, cancellation, and empty results keep
   the usable catalog. Reader selection is preserved by identity during updates.
   Disk checkpoints are throttled during growth; a completed catalog is persisted.
2. Novel metadata is limited to 256 cache entries, complete repository catalogs
   to 16, and first-chapter warmup markers to 64. Snapshot storage strongly retains
   16 recent flows and weakly tracks externally held flows. A reference queue
   removes dead registry entries; active consumers keep their flow identity even
   after LRU eviction. Active screens may intentionally retain their own data;
   this is not a fixed byte ceiling for all UI-owned memory.
3. Settings clears HTTP and image caches through their live owners, and the
   shared ReaderCacheStore through its reader/writer mutex. Narration keeps its
   existing protection. Repeated Settings clear requests are coalesced; failures
   in one owner do not skip the others. Active work can populate fresh entries
   after a clear, so a zero-byte result is not guaranteed. Arbitrary cache-folder
   files and third-party journals are no longer recursively deleted.
4. Both NovelPhoenix catalog paths use three workers and small bounded work/result
   queues. Page counts over 1,000, including integer overflow, are rejected before
   scheduling. Ordered progressive emission, cancellation, and failed-page
   reporting remain in place. The cap is centralized in the parser.
5. Mini-player progress reads frame-by-frame values in Canvas drawing. Time labels
   observe derived whole seconds and cache formatted text. The track, gradient,
   controls, and drag thumb retain their existing visual treatment.

Verification after the changes: **138 selected offline tests passed**, including
10 new regression tests in SystemDesignRegressionTest and NovelPhoenixPaginationTest.
The additional suites cover parser fixtures, source routes, reader startup and
stability, cache persistence/cancellation, browsing, search, Home history, ratings,
identity, and notification policy. They do not make live site/cloud requests.
Phone profiling and Android lifecycle interaction checks remain pending.
The final sources passed the same 138-test selection again (zero failures,
errors, or skipped tests), and `:app:assembleDebug --offline --max-workers=1`
completed successfully. The fresh APK is `app/build/outputs/apk/debug/app-debug.apk`.

Cache API reference: [Coil 2.7 DiskCache](https://github.com/coil-kt/coil/blob/2.7.0/coil-base/src/main/java/coil/disk/DiskCache.kt).

## Notification launch follow-up — 2026-10-24

A main-thread null-icon failure was
found in MainActivity's splash exit path with core-splashscreen 1.0.1. The icon
handling and splash lifetime were repaired, notification routing now waits for
graph/lifecycle readiness, and notification/detail loading failure boundaries
were strengthened. See [notification launch investigation](NOTIFICATION_LAUNCH.md)
for evidence, changes, scenario handling, and device-verification limits. This
follow-up used compilation and dependency inspection; the earlier 138-test
results above do not describe these new lifecycle changes.
The final notification repair APK assembled successfully; physical-device
confirmation remains pending.

Connected-device narration follow-up (2026-10-24): the installed build's crash
buffer confirmed `ForegroundServiceDidNotStartInTimeException` from transport
commands. Transport now dispatches to a live session, explicit Listen always
acknowledges foreground startup, rapid skips are combined into one destination,
and remote failures receive bounded admission/cooldown handling. See
[connected-device investigation](CLOUD_NARRATION.md#connected-device-investigation--2026-10-24).
The final APK assembled; post-fix device reproduction awaits manual installation.

## Initial conclusion and scope (before repairs)

The initial implementation had useful safeguards and concrete correctness and
efficiency issues. It was not verified for every scenario. This was a
focused audit of repository/cache ownership, source pagination, reader startup
and scrolling, narration service/client/mini-player, and notification scheduling.
It is not an exhaustive review of every screen, database migration, Android
version, device vendor, or live source response. Production code was not changed
during the initial inspection; the repair update above describes subsequent work.

## Initial findings, in recommended repair order

### 1. P1 — Partial catalog refresh replaces a larger usable list in memory

`NovelRepositoryImpl.kt:421` unconditionally assigns each live pagination result
to the shared chapter snapshot. `NovelDetailViewModel.kt:299` also replaces its
displayed list with every successful result. The size guard at repository line
441 protects the persisted catalog, not either of these in-memory lists.

Example: a stored 2,000-chapter catalog is emitted, a fresh first page containing
100 chapters arrives, and page two fails. The detail page now retains only 100
chapters until another successful load, even though the larger catalog remains
on disk. The reader's own incoming-size guard helps an already-open reader but
does not protect a newly opened reader consuming the smaller shared snapshot.

Recommendation: distinguish partial snapshots from validated complete refreshes.
Retain the usable catalog and merge partial data by canonical chapter identity;
only reconcile removals after a complete refresh. Preserve the selected chapter
by identity. Add regression coverage for a larger cached catalog followed by a
smaller first page and later-page failure.

### 2. P2 — Several process-lifetime maps are unbounded

`NovelRepositoryImpl.kt:66-71` retains chapter lists, chapter snapshot StateFlows,
prefetch markers, and novel metadata in maps/sets without eviction. The bounded
eight-entry reader content cache and source LRUs do not bound these collections.
Browsing and opening distinct novels throughout a long session increases retained
metadata/catalog memory until process death.

Recommendation: define separate memory budgets and bounded eviction policies.
Snapshot entries with active consumers require pinning or explicit ownership;
blind eviction could disconnect existing observers. Preserve durable reading
history/bookmarks independently from disposable metadata caches.

### 3. P2 — General cache clearing bypasses active cache owners

`SettingsViewModel.kt:64` recursively deletes files under `cacheDir`, excluding
only narration audio. This also reaches the active OkHttp and Coil caches and
the reader resume cache without their own clear APIs/locks. Source fetching,
image loading, or reader cache writes can overlap that deletion.

Recommendation: clear each cache through its owner. Use OkHttp/Coil cache clear
operations and a serialized clear operation on ReaderCacheStore. Coordinate
reader cache writes with clearing and retain the existing narration protection.
Do not claim that a clear operation is atomic across these independent caches.

### 4. P2 — NovelPhoenix bounds active requests, not pagination job allocation

`NovelPhoenixSource.kt:300-302` allocates a channel sized to the reported page
count and launches one coroutine for every remaining page. Its semaphore limits
active requests to three, but queued jobs and the channel still grow with page
count. The alternate complete-list path similarly allocates one Deferred per
page. `NovelPhoenixParser.kt:207` only applies a lower bound to the parsed count.

Recommendation: validate plausible pagination bounds and use a fixed number of
workers consuming page numbers with a small bounded channel. Keep progressive
results ordered and cancellation cooperative. This is a scaling/input robustness
finding, not evidence of a current source response causing a crash.

### 5. P3 — Mini-player progress performs composition work every animation frame

`NarrationMiniPlayer.kt:461` reads animated progress in composition. The same
component computes formatted elapsed/duration strings at lines 548-570. While
visible playback is active this adds per-frame composition/allocation work even
though the displayed seconds usually remain unchanged.

Recommendation: draw the progress fill/thumb in the draw phase and update the
time label only when its displayed second changes. Retain lifecycle suspension
and local state isolation. This is an optimization opportunity; temperature and
frame-time improvements have not been measured.

## Safeguards present in the reviewed code

- Reader uses lazy paragraph rendering, stable row keys, progressive catalog
  startup, a bounded nearby-text retention window, and saved scroll anchors.
- Fast scrolling uses one conflated request channel and a frame-paced consumer;
  its percentage is approximate and it suppresses chapter-pull gestures.
- Reader content requests use source-aware keys, request locks, an eight-entry
  repository LRU, and a bounded disk cache with atomic replacement.
- Discover delays missing-rating lookups, limits concurrency to two, and has
  cancellation/cooldown handling rather than issuing a detail request per frame.
- Narration owns playback in a foreground service, has bounded downloads and a
  separate audio cache, cancels pending work, and has no local speech synthesis.
- Notification update checks are limited to ten books per pass and reminder
  timing is scheduled rather than driven by a continuously running timer.

These are code-level observations, not measurements of zero heat, guaranteed
notification delivery times, or survival after force-stop/process termination.

## Initial verification performed (before repairs)

Existing offline tests were run with:

```text
:app:testDebugUnitTest --offline --max-workers=1
  --tests '*ReaderStabilityTest' --tests '*ChapterStartupTest'
  --tests '*NovelPhoenixRouteTest' --tests '*FreeWebNovelRouteTest'
  --tests '*ProfileNotificationTest' --tests '*DiscoverRatingTest'
```

| Suite | Tests | Failures/errors |
|---|---:|---:|
| ReaderStabilityTest | 6 | 0 |
| ChapterStartupTest | 8 | 0 |
| NovelPhoenixRouteTest | 6 | 0 |
| FreeWebNovelRouteTest | 6 | 0 |
| ProfileNotificationTest | 16 | 0 |
| DiscoverRatingTest | 10 | 0 |
| **Total** | **52** | **0** |

The tests use fixtures/fakes for the selected scenarios. Passing them does not
validate current production HTML, live cloud inference, Android service behavior,
TalkBack, or device thermal performance. No tests were added in this audit.
No existing automated narration/fast-scroller tests were found in the Android
unit/instrumentation test directories.

ADB reported no connected devices. Pending device checks include lock/unlock,
calls/audio-focus changes, Bluetooth disconnect, Recents removal, force-stop,
process recreation, offline/slow networks, quota and decoder errors, cache clear
during activity, long-session memory, font/rotation changes, scroller cancellation,
accessibility, and frame-time/temperature measurement on representative phones.
