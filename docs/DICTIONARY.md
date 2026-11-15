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
visibility. The manifest now also declares scoped PROCESS_TEXT queries, as Android
requires for discovering text processors; it does not request broad package access.
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

The exported ACTION_PROCESS_TEXT entry remains available to other compatible apps;
its visibility there is controlled by those apps and the device. The web-novel
reader currently uses plain Compose Text paragraphs with no word-selection UI;
adding that selection capability is separate work. The data repository is shared
and can serve future reader integrations without another cache or API client.

First version is English, one word (maximum 80 UTF-16 code units), including
apostrophes/hyphens and accented letters. Surrounding quotes/punctuation are
removed; Unicode NFC and locale-independent lowercase give a consistent key.
There is no stemming, AI fallback, chapter upload, local model, audio playback,
background prefetch, or subscription/key setup. Free Dictionary API receives only
the requested word. API availability and public IP-based limits still apply.

## Ownership and request flow

NovelNestApplication lazily owns DictionaryRepository. DictionaryLookupActivity
and DictionaryViewModel own one foreground UI query. Rotation retains the running
ViewModel operation; closing/backgrounding cancels the caller, and a cancelled
older query cannot overwrite the next word. Process recreation saves only the
short normalized query and consults cache when the panel becomes visible again.
A background-cancelled lookup exposes Refresh rather than silently retrying.
Exported intent inputs are action/type/length validated, including bad-parcel
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
URLs and entry license names/links are preserved and displayed. Pronunciation text
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

Final offline assembleDebug: BUILD SUCCESSFUL in 24 seconds, with no Kotlin
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

Follow-up verification: offline assembleDebug succeeded in 14 seconds with no
Kotlin compiler warnings. APK manifest inspection confirmed the scoped queries
intent and existing exported processor. Git diff --check passed. No automated
tests were added or run for this follow-up. Device behavior remains unverified.
