# Dictionary lookup

## Availability and scope

Long-press a word in the EPUB reader, then choose **Define in Novel Nest** from
Android's text-selection menu (it may be under More). The reader appends this local
action to the actual WebView ActionMode callback, preserving its native Copy,
Share, selection handles, content rectangle and teardown callbacks. Both typed
and legacy parent entry points are covered without wrapping twice. No renderer,
chapter-navigation, notes, narration or progress behavior is replaced.

The local action is necessary because Samsung's WebView can filter PROCESS_TEXT
activities through its Manage apps enabled list, including hiding them when that
list has not been configured. Exporting an Activity alone did not guarantee menu
visibility. The manifest retains scoped PROCESS_TEXT queries so the native menu can discover
other text-processing apps; these queries do not advertise Novel Nest's private
dictionary or request broad package access.
A duplicate native entry for this app is hidden by item instance, preserving other
menu actions even when they share the same item ID.

A fixed, one-shot JavaScript expression captures selection only after the user taps
Define and limits it to 512 UTF-16 units before crossing the bridge. Only a valid
single word launches the explicit floating DictionaryLookupActivity. Invalid
selections keep the handles available for adjustment. Repeated taps while capture
is pending are ignored. Destroyed selections, changed WebView documents, detached
views, replaced navigators and non-resumed readers cannot launch a delayed lookup.
The view's launch callback is cleared when Compose releases its host. There is no
selection polling, clipboard round-trip or automatic dictionary network request.

DictionaryLookupActivity is non-exported and has no PROCESS_TEXT intent filter.
Only Novel Nest can open it; it is not advertised in other applications' selection
menus. The reader's explicit component Intent retains the action/type/text payload
for its existing input validation, without requiring system-wide registration.
The web-novel reader now tracks one chapter-wide text range across virtualized
paragraph rows, including decoded and rewritten displayed text. Its private native
floating ActionMode provides Copy, Select All and Define in Novel Nest. Define is
shown only for a valid single word and opens the same non-exported dictionary
Activity explicitly. No chapter upload, clipboard lookup or system-wide menu
registration is added. See [webnovel selection architecture](READER_TEXT_SELECTION.md)
for handle dragging, edge scrolling, lifecycle ownership and lazy-list constraints.
The shared repository and pronunciation/cache implementation serve both readers.

First version is English, one word (maximum 80 UTF-16 code units), including
apostrophes/hyphens and accented letters. Surrounding quotes/punctuation are
removed; Unicode NFC and locale-independent lowercase give a consistent key.
There is no stemming, AI fallback, chapter upload, local model, automatic audio playback,
background prefetch, or subscription/key setup. Free Dictionary API receives only
the requested word. API availability and public IP-based limits still apply.

## Ownership and request flow

NovelNestApplication lazily owns DictionaryRepository. DictionaryLookupActivity
and DictionaryViewModel own one foreground UI query. Rotation retains the running
ViewModel operation; closing/backgrounding cancels the caller, and a cancelled
older query cannot overwrite the next word. Process recreation saves only the
short normalized query and consults cache when the panel becomes visible again.
A background-cancelled lookup exposes Refresh rather than silently retrying.
Internal intent inputs remain action/type/length validated, including bad-parcel
handling. No selected-text result is returned, so editable source selections are
never replaced. No launcher, task-affinity, boot, or service behavior is changed.

DictionaryApi uses one isolated OkHttp client/dispatcher, maximum two active HTTP
calls, 5-second connect, 8-second read and 12-second whole-call deadlines. Redirects,
connection retries and HTTP disk caching are disabled. Responses close on all
paths; decompressed bytes are streamed in 8-KiB buffers with a 512-KiB limit.
Socket cancellation is dispatched off Main. JSON parsing happens off Main and
bounds entries, meanings, definitions and field sizes. Plain native text is used
for API output; it is never executed as HTML, JavaScript, or a file path.

The repository shares each same-word/generation operation between callers with
reference counts. The final departing caller marks the operation abandoned before
cancelling it, preventing a new caller from joining a request being torn down.
Completed operations remove themselves. The registry itself is bounded to two;
additional distinct requests receive a retryable Busy result instead of creating
an unbounded queue. No Activity or View is held by shared requests.

HTTP 404 is negatively cached in memory for 10 minutes, maximum 32 words. Network,
timeout, malformed and server failures are not dictionary entries. HTTP 429 honors
numeric/date Retry-After (bounded to seven days); missing instructions use 60
seconds. The cooldown uses elapsedRealtime, survives sleep, and is shared by all
panels in this process. It does not survive process death. Cache clearing does not
bypass it. The UI schedules one visible expiry update, never per-second polling or
an automatic HTTP retry. A request already accepted can still count at the provider
after cancellation.

## Cache policy

Cache key: SHA-256(provider + API version + schema version + en + normalized word).
It excludes book/chapter identity. Definitions can be reused across books. Source
URLs and entry license names/links are preserved and displayed. Pronunciation text, user-started recordings
and optional examples are supported; missing examples/audio are never fabricated.
Only HTTPS source/license links are opened, and only after an explicit tap.

- Memory: 32 bounded entries.
- Cached entry: maximum 64 KiB of serialized data. Larger valid results can be
  displayed but are not inserted into either cache tier.
- Disk: cacheDir/dictionary, 8 MiB and 256 records maximum.
- Freshness: 30 days using the fetched timestamp, independent of LRU access time.
- Stale records: shown immediately while one foreground refresh is attempted.
  Refresh errors keep the old definition visible. Future timestamps caused by a
  clock change are treated as stale.
- Disk reads, validation, maintenance, writes and eviction run on IO. There is no
  network call while holding the cache mutex. In-memory hits do not rewrite JSON;
  LRU file timestamps are touched at most once per minute per memory entry.
- Writes use an owned temporary file and atomic replacement where supported.
  Interrupted/corrupt/oversized records are recovered on demand. Space is reserved
  by evicting old records before another disk write. Failed eviction skips the
  write; failed writes or temporary-file cleanup disable further disk writes
  until a successful cache clear or the next process. Memory and fetched results
  remain usable.
- Settings -> Storage & Cache -> Clear Cache clears this owner alongside existing
  cache owners. A generation counter prevents earlier requests from repopulating
  memory/disk after clearing. Bookmarks, reading history, notes, EPUBs and saved
  progress remain in their existing persistent stores.

This cache is disposable and Android may remove it. It is not a downloaded offline
dictionary or a saved vocabulary library. No Room migration or additional Android
permission/dependency is introduced. INTERNET already exists in the app manifest.

## Verification and remaining checks

Build: build/diagnostics/dictionary/build.log. No automated tests were added or run
for this implementation. Before release, verify the native action and return path
on Samsung and Pixel, all five themes, dark/light system settings, large font sizes,
rotation, app backgrounding, malformed/long selections, apostrophes, accented words,
misses, repeated queries, missing examples, cold/offline cache hits, stale refresh,
404/429/503, timeout, corrupted cache, disk-full/read-only behavior, concurrent cache
clear and rapid close/reopen. Verify EPUB selection scrolling, Copy, chapter swipes,
progress restoration and ongoing narration alongside the panel. No phone performance
or thermal profile has been captured.

References:
- https://dictionaryapi.dev/
- https://github.com/meetDeveloper/freeDictionaryAPI/blob/master/app.js
- https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT

Historical initial implementation (public registration, superseded below):
Offline assembleDebug: BUILD SUCCESSFUL in 24 seconds, with no Kotlin
compiler warnings in that build. AAPT inspection confirmed the APK contains the
exported DictionaryLookupActivity, PROCESS_TEXT action and text/plain filter.
Git diff --check passed. ADB device inspection could not run because its Android
configuration directory was inaccessible. No APK was installed and no phone
selection-menu behavior is claimed verified.

### Selection menu compatibility follow-up

The EPUB selection host now owns the Define menu action rather than relying on
OEM text-processor discovery. Build log:
build/diagnostics/dictionary-selection-action/build.log. Phone confirmation of
menu display, selection-handle behavior and dictionary return is still required;
compilation and APK inspection cannot establish device runtime behavior.

References for the compatibility fix:
- https://developer.android.com/training/package-visibility/use-cases#implement-custom-actions
- https://chromium.googlesource.com/chromium/src/+/0d4c0e490b48d360f5ab6fbcdb673c3cd5a5f31e/android_webview/java/src/org/chromium/android_webview/selection/SamsungSelectionActionMenuDelegate.java

Historical selection-menu follow-up (public registration, superseded below):
Offline assembleDebug succeeded in 14 seconds with no
Kotlin compiler warnings. APK manifest inspection confirmed the scoped queries
intent and existing exported processor. Git diff --check passed. No automated
tests were added or run for this follow-up. Device behavior remains unverified.

## Pronunciation recordings

The speaker beside the selected word plays the first supported recording identified
by Free Dictionary API. For a dictionary-owned MP3 mirror with an attributed Commons
page ID, the app resolves and downloads the exact original Wikimedia recording.
This avoids mirror addresses that currently return HTTP 404. Older supported
gstatic addresses and recordings without a recognized Commons source retain their
direct download path. No word-derived recording URL or replacement voice is guessed. The displayed phonetic text prefers that recording's own
transcription. Missing audio disables the speaker and shows an unavailable caption.
The UI exposes loading, playing, stop/cancel and retryable errors without replacing
definitions. Recording source and license metadata are retained and displayed when
provided. No accent/voice selector or synthetic pronunciation fallback is included.

Only known HTTPS recording origins are accepted: api.dictionaryapi.dev under
/media/pronunciations/, ssl.gstatic.com under /dictionary/static/sounds/, and
upload.wikimedia.org under the original Commons hash/file paths with supported
audio extensions. Protocol-relative links become HTTPS. MediaWiki's three known
utm tracking parameters are stripped from original-file URLs; other queries,
credentials, custom ports, fragments, other origins and redirects are rejected.

Commons resolution uses a fixed commons.wikimedia.org/w/api.php request, only a
validated positive curid from the attributed /w/index.php source URL, one page and
one imageinfo revision. Metadata is capped at 32 KiB; the returned page ID, file
namespace, MIME and original-file origin/path are checked. Resolution and download
share an 18-second total deadline; each HTTP call retains its 12-second limit.
The same single-transfer client/lease is used, with no background resolution,
metadata prefetch, automatic retries or scraper/narration credentials. Wikimedia
receives the recording page ID and media request only after a speaker tap.
[MediaWiki imageinfo documentation](https://www.mediawiki.org/wiki/API:Imageinfo)
describes the original-file URL and MIME lookup.
Official dictionary payload example: https://dictionaryapi.dev/

DictionaryAudioStore is owned by the shared repository. Recordings stream to an
owned temporary file in 8-KiB buffers with a 512-KiB per-file limit. A separate
4-MiB / 64-file LRU lives at cacheDir/dictionary/audio. There is at most one active
transfer/playback lease, at most one waiting caller (up to 1.5 seconds), and at
most one active temporary file during download. A new Play can wait briefly for
a cancelled transfer or player to finish releasing its lease; further waiters
receive Busy instead of creating an unbounded queue.
No audio byte array, PCM generation, prefetch, automatic retry or progress polling
is introduced. Downloads use 5-second connect, 8-second read and 12-second overall
timeouts. Audio MIME or application/ogg is required; Wikimedia uses the latter for
valid Ogg/Vorbis recordings supported by Android. MediaPlayer validates decoding and clips over
30 seconds are rejected. A 35-second decoder/playback deadline bounds missing
callbacks. Failed decoding evicts the clip so an explicit retry can refetch it.

Cache hits avoid directory maintenance and sorting; those run only on cache
misses and before new file commits. Network operations never hold the cache-file mutex. Clear Cache increments an
audio generation and deletes idle files; the active file is protected until its
lease ends, then removed. A download already accepted may finish for its current
listener but cannot repopulate cleared cache. Cancellation closes the call off
Main and joins an already-running response reader before deleting its temporary
file or releasing the playback/transfer lease. If cancellation wins before the
response callback starts, that callback can only close its response and cannot
open a file. This prevents late writers from racing cache maintenance or a rapid
Stop/Play. Only an active bounded reader is joined; cancellation during DNS or
while queued does not wait for a callback that has not started. Cache-clear
generation is captured before waiting for a lease, so a queued pre-clear Play
also cannot repopulate the cache after it is cleared.
File maintenance, hashing, streaming and eviction run on IO. The native player
uses asynchronous preparation of a local bounded file.

DictionaryLookupActivity owns DictionaryPronunciationPlayer and stops it on
pause, new selected word, refresh, close or destruction, including rotation and
screen lock. Rotation retains the definition request but never restarts audio.
Headphone disconnect and audio focus loss also stop it. No foreground service,
notification, new permission or dependency is needed. Resource cleanup uses player
identity checks so an older cancelled operation cannot release a newer player.

Audio focus is transient and requested only after preparation, immediately before
playback. It uses speech attributes. Existing narration handles transient loss by
pausing and resuming on focus return if no user pause superseded it. Pronunciation
abandons focus on completion, error or stop; it does not directly issue narration
service commands or change listening/reading progress. Other media apps also get
normal system focus handling. Android guidance:
https://developer.android.com/media/optimize/audio-focus

The audio cache identity includes the source page ID and resolver version. Cache
hits skip both metadata and audio requests, including offline. Existing schema-2
definitions already contain the attributed source IDs, so the fix needs no
definition refresh or cache wipe. Old audio files remain subject to the same
bounded LRU and normal cache-clear operation.

Definition cache schema 2 preserves recording metadata. Schema 1 records remain
readable offline, retaining their fetched dates, and are treated as stale so one
normal foreground refresh can discover recordings. Bookmarks, notes and progress
need no migration. The audio directory participates in the existing Settings
Clear Cache operation without touching narration's audio cache.

Verification: build/diagnostics/dictionary-pronunciation/build.log. Automated tests
and phone playback checks have not been run. On-device checks still needed: cached
and first playback; offline recording; unavailable links; rapid play/cancel/play;
word change and backgrounding during download/preparation/playback; rotation;
Bluetooth/headphones and calls; active narration focus pause/resume; corrupt or
oversized recording; storage failures; concurrent cache clear and late callbacks.

Final pronunciation verification: offline :app:assembleDebug succeeded in 1m 38s
with no Kotlin compiler warnings in the captured build log. Git diff --check
passed. No live recording playback or phone audio-focus handoff was verified.

## App-only dictionary entry (16 November 2026)

The initial exported PROCESS_TEXT intent filter made Define in Novel Nest visible
in compatible external applications. The EPUB reader already installs its own menu
item and launches DictionaryLookupActivity explicitly, so public registration was
unnecessary. The Activity is now android:exported="false" and its PROCESS_TEXT
intent filter is removed. Other apps cannot discover it as a text processor or
launch the component under a different UID.

The existing reader callback, single-word validation, explicit Intent payload,
floating panel, definition cache, pronunciation and lifecycle behavior are retained.
Scoped PROCESS_TEXT queries stay in the manifest to preserve other native text
processors; discovery queries and advertised receiver filters have different roles.
No database migration, permission, dependency, renderer change or network request
is introduced by this restriction. Existing external apps may cache their old menu
until their selection toolbar is recreated after the app update.

Build/manifest verification is recorded under
build/diagnostics/dictionary-private-entry/. Earlier public-registration results
above describe the historical implementation, not the current manifest contract.
Android component visibility reference:
https://developer.android.com/guide/topics/manifest/activity-element#exported

App-only verification: offline assembleDebug succeeded in 1m 26s. Inspection of
the packaged APK confirmed DictionaryLookupActivity has exported=false and no
intent-filter. The explicit EPUB launch path and menu callback were inspected;
their executable code is unchanged. Git diff --check passed. No automated tests
were added or run, and no emulator/device was connected for runtime verification.


## Missing pronunciation mirror fix (16 November 2026)

Live inspection reproduced successful definition responses but HTTP 404 from all
six supplied pronunciation mirrors for hello, world, ability and chapter. Their
exact attributed Wikimedia originals returned HTTP 200. The originals use
application/ogg, which the old audio-only MIME check also rejected. The UI
previously reported every non-success status as temporarily unavailable, masking
permanently missing files. Errors now distinguish missing files, rate limits,
server failures and rejected requests, including HTTP status.

The shared recording store now uses the verified attributed original for mirrored
recordings, accepts application/ogg and retains bounded streaming, leases,
cancellation cleanup, cache clearing and foreground-only player ownership.
Definition UI, reader navigation and narration are unchanged. No local TTS, paid
provider, new permission, dependency or database migration is added.

Android format reference: https://developer.android.com/media/platform/supported-formats

Verification artifacts: build/diagnostics/dictionary-recording-source/. Live HTTP
and audio-byte inspection and offline debug assembly are used for this fix.
Actual on-device speaker output, Bluetooth/focus handoff and lifecycle gesture
checks remain to be verified; no automated tests were added or run.


## Pronunciation lifecycle refinement (16 November 2026)

Review found that prompt coroutine cancellation could release a cache lease while
an OkHttp callback was still closing or writing its partial file. A subsequent
Play could then race file deletion/maintenance. Response-reader admission and
cancellation now share a short lock, and an admitted reader signals completion
after its streams close. The owner joins that signal off Main before file cleanup
and lease release. Late, unadmitted callbacks never touch a temporary file.

Only one caller may wait up to 1.5 seconds for the previous lease. Timeout and
cancellation also release a lock won at the timeout boundary. Stop remains
immediate for the UI/player, while disk/network teardown finishes safely on IO.
The prepared callback now cancels immediately when the dictionary is inactive
instead of leaving its completion pending until the playback deadline.

HTTP 429 and 503 (or another 5xx with Retry-After) record a per-host monotonic
cooldown. Numeric/date Retry-After is bounded from one second to seven days;
missing/invalid instructions use 60 seconds for 429 or 15 seconds for service
failures. At most four known hosts are retained. Repeated taps fail locally during
cooldown, and cached recordings still play. No timer, polling, automatic retry or
new background task is scheduled. Cache clearing does not bypass provider backoff.

Verification artifacts: build/diagnostics/dictionary-pronunciation-refinement/.
Offline debug assembly and scoped diff review are performed. Automated tests and
physical-device rapid Play/Stop, cache-clear and audio-focus checks remain pending.


## First-tap pronunciation rejection review (16 November 2026)

There is no local one-word or per-minute pronunciation counter. A fresh mirrored
recording with Commons attribution uses one metadata lookup followed by one audio
download. A cached clip uses neither. The host cooldown is created only after a
server returns HTTP 429, HTTP 503, or another 5xx with Retry-After; it is separate
from the definition API cooldown and from cloud narration.

Two request-policy gaps were found: the pronunciation User-Agent omitted the
project contact URL required by Wikimedia, and the interactive metadata query
included maxlag=5, which can reject work because of replication lag unrelated to
this user's request count. Pronunciation now identifies the real project URL and
omits maxlag for this explicitly user-initiated lookup, as MediaWiki permits.
No background metadata fetch is introduced.

The bounded per-host cooldown now retains the actual HTTP status. Both the first
rejection and subsequent local cooldown errors identify the host, distinguish
HTTP 429 rate limits from 5xx source outages, and show the remaining wait. Valid
Retry-After instructions are still respected. Successful cached clips remain
playable. No automatic retry, alternate-host quota bypass, local synthesis,
permission, dependency, migration, or reader UI change is introduced.

Live inspection of the exact user examples, 'the' and 'almost', returned HTTP 200
from Dictionary API, Commons metadata and the original Ogg recordings. Sizes were
18,040 and 16,569 bytes, respectively, with application/ogg MIME and OggS headers.
The earlier request also succeeded for the sample 'hello'. These observations do
not reproduce or establish the phone's exact server rejection; shared network
traffic or a transient provider policy/capacity response remains possible.

Verification: offline :app:assembleDebug succeeded in 1 minute. Scope review and
git diff --check passed. No automated tests were added/run and no phone playback
was verified. Artifacts: build/diagnostics/dictionary-first-pronunciation/.

References:
- https://www.mediawiki.org/wiki/Wikimedia_APIs/Rate_limits
- https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy
- https://www.mediawiki.org/wiki/Manual:Maxlag_parameter
