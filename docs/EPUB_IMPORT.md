# Local EPUB library

## Using it

Home's **EPUB** letter button opens the dedicated EPUB Library. Its header imports
one or multiple EPUBs using Android's document picker (up to 100 selections per batch).
The picker accepts generic ZIP/binary MIME labels for providers that mislabel EPUBs;
validation still requires a real DRM-free, reflowable EPUB 2 or EPUB 3 publication
(maximum compressed size 100 MiB per file). Imports are processed sequentially.

The page has a Continue card, up to ten grouped Recently Read entries, and a
three-column My Library grid. Create a series to group separate volume files.
Inside a series, Import EPUBs captures that series as its destination. Long-press
a card for Move, volume labels, rename, volume order, and removal actions.
Series deletion keeps its volumes and reading positions in My Library. Removing an
EPUB separately asks for confirmation and removes only the app-owned copy.

Continue and Recently Read resume a particular volume's saved position. A series
appears once in the main grid/history, not once per volume. Individual volume
progress is never averaged into a fabricated series percentage. Home's website
tabs retain their source-specific Continue cards. EPUBs remain independent of
website source IDs, bookmarks and update jobs. See [EPUB_LIBRARY.md](EPUB_LIBRARY.md).

## Architecture

- Readium shared/streamer/navigator 3.0.3, pinned for Kotlin 2.0.21 / SDK 35
  compatibility; only EpubParser is used. No PDF, DRM, audiobook or OPDS adapter. The BSD license notice is included in
  assets/licenses/readium.txt.
- EpubRepository owns one application-scope import, bounded streaming copy/SHA-256,
  archive validation, metadata/thumbnail extraction, and durable storage.
- Room version 6 introduced local_books and epub_positions. Version 7 adds series
  organization through the registered, additive 6-to-7 migration. Existing website,
  notes, terms, manhwa and per-volume progress tables are retained.
- Local IDs are epub- followed by the entire SHA-256 fingerprint. They never enter
  NovelSourceType, BookmarkManager, NovelRepository or remote chapter update jobs.
- EpubReaderActivity hosts Readium fragments separately from MainActivity. Readium's
  publication-dependent fragments are rebuilt with their factory instead of being
  restored without a Publication. A retained ViewModel supplies the latest locator
  during rotation; Room supplies it after process recreation.
- Application activity tracking spans both activities, so reading reminders do not
  treat an open EPUB reader as inactivity. Reading days include EPUB activity.

## Files, cache and lifecycle

Permanent private files: files/epub_library/<id>/book.epub and cover.jpg. There is no
whole-book text cache or whole-archive extraction. Resources are loaded on demand.
The imported copy remains usable if the selected original moves or is deleted.
No broad storage permission, persistable URI grant, network upload or foreground
service is added. Selected-provider access is needed only while copying.

A single import streams 32-KiB buffers on Dispatchers.IO. It survives rotation and
leaving the library page while the process lives. Completed volumes survive cancellation. Process death interrupts import; next
initialization removes staged .import-* folders. A pending-import marker identifies interrupted finalization; only journal-proven
uncommitted folders are removed. Unexplained durable files without a database row
are preserved. Files are finalized before the database record is inserted;
the short commit section is noncancellable. An interrupted import must be retried,
not assumed to resume automatically. Confirmed deletion writes a pending-delete marker, then removes the row and the
app-owned files without cancellation; startup completes journaled deletions.

Copy/parsing use an independent import lock. The brief catalog commit/delete lock
also coordinates progress writes, so a long import does not block checkpoints.
Latest checkpoints are conflated per book, written at most every two seconds while
moving and flushed on pause/close. Application-scope storage writes outlive reader
ViewModels; each failure has three bounded attempts and a visible Retry action.
No per-frame database writes or progress polling. EPUB locators/progression are
separate from novel chapter counts and manhwa image offsets.

Cloud backup excludes large publication files to avoid crowding out other app
backup data. Metadata and locators remain backed up; importing the same original
repairs a missing file without resetting its position. Modern device transfer may
include publication files. Uninstall removes private copies, not original documents.

## Input and resource policy

Actual expanded bytes are bounded: 512 MiB total, 20 MiB per binary resource,
4 MiB per markup resource, 10,000 ZIP entries. Reject unsafe/duplicate ZIP paths,
missing container/mimetype, XML entity declarations and active SVG. Readium handles
metadata, OPF spine order, EPUB 3 navigation and EPUB 2 NCX fragment targets.

HTML resources are sanitized on read, selected by declared media type as well as
filename. Publication scripts, event handlers, forms, embedded frames and redirect
metadata are removed. An injected CSP blocks remote resources and network requests;
only local reading-system scripts/styles/resources are permitted. The engine's HTTP
client refuses all requests. External links are disabled and visibly reported.
Reading-order documents that require a remote server are rejected. Missing optional
resources or render failures show a reader message rather than an uncaught failure.

DRM and fixed layout are explicitly unsupported in this version. Interactive books,
remote media and embedded audio/video are not preserved as executable content.
System serif/sans/monospace fonts map from shared preferences; bundled novel fonts
are not yet registered as EPUB font declarations. Font size, line height, theme and
continuous/paged preferences are reused. Text size/theme controls are available in
the EPUB reader. AI tools, notes/terms, cloud narration, auto-scroll and the novel
quick settings sheet are not wired into EPUBs; these require dedicated adapters.

## Reliability audit (2026-11-12)

Confirmed gaps and fixes:

- **Resource policy was not installed.** The pinned Readium 3.0.3 binary ignores
  the PublicationOpener constructor callback and invokes its per-open callback
  twice. Real EPUB 2/3 integration tests reproduced raw, unsanitized HTML without
  the offline CSP. ReadiumEpubEngine now passes an idempotent callback directly to
  open(): OfflineEpubContainer is installed once, before publication services are
  created. No library upgrade or Android compatibility change was needed.
- **Resource errors could escape rendering coroutines.** EpubMarkupResource
  converts oversized/malformed markup and resource allocation/read failures into
  Readium errors, retaining cancellation. It also avoids the 3.0.3 double transform
  in read(null) with cacheBytes=false. Section data is not retained across reads.
- **Navigator readiness and TOC results could be misleading.** Controls wait for
  attachment, preferences are reapplied when the navigator becomes available,
  and navigation is guarded by resumed activity/fragment state. TOC destinations
  must match a reading-order resource; invalid targets show an error. Readium's
  go() can otherwise return true without finding a matching resource. Valid
  fragment targets continue to work. Launches validate local IDs, support an
  application context, and report launch failures from Home.
- **Closing and saving could fail on Main.** Checkpoint serialization failures
  are reported, and Publication release runs on the repository IO dispatcher with
  cleanup exceptions contained. Cancelled opening releases the publication once.
  A restored percentage is retained when the initial locator lacks aggregate
  progression. Invalid checkpoints cannot silently replace saved progress, and
  conflation uses arrival order rather than a potentially backwards wall clock.
- **Import cancellation could race a replacement or stay stuck before startup.**
  The import job is assigned before starting, completion handles cancellation even
  before the body executes, and a replacement waits until prior cleanup completes.
  An unresponsive document provider can still delay a blocking read's return;
  cancellation is displayed and overlapping imports are prevented during cleanup.
- **A transient recovery failure was permanent until process restart.** Library
  Retry and a subsequent import can now retry recovery. Failed folder access is
  reported; unexplained durable files are preserved.

The EPUB activity, identifiers, files, local tables and locators remain independent
of website sources. This audit adds no database migration, storage permission,
network service, or dependency in the production APK. The existing coroutine test
library is also used by Android lifecycle tests.

## Verification

- **48 focused offline tests passed**, with zero failures or skips.
- **15 instrumentation tests passed** on the Pixel 8 AVD, Android API 36.
- Both debug APKs compiled and packaged. The final diff passed whitespace checks.

Offline tests: EpubArchiveValidatorTest, EpubContentSanitizerTest,
EpubReliabilityTest, HomeEpubContinueTest, HomeHistoryTest, ReaderEntrySourceTest,
ProgressPersistenceTest and BookmarkSnapshotWriterTest. They cover resource-error
containment, one read per request, range handling, cancellation propagation,
bounded checkpoint retries, recovery retry, invalid checkpoints, backwards-clock
ordering, deleted-book protection and asynchronous cleanup failures.

Device tests: EpubImportIntegrationTest uses real Readium + Room against generated
EPUB 2/3 fixtures: metadata, spine order independent of ZIP/manifest order, TOC
fragments, offline sanitization, duplicate import, retained progress, missing-file
repair, independence from the original, and deletion. Its real reader activity
also renders chapter text in WebViews, changes chapter, recreates the activity,
backgrounds/resumes, and verifies the durable locator. EpubLifecycleTest covers
controlled cancellation/opening faults, retry, invalid locator serialization,
restored percentage and valid/invalid TOC targets. All five MigrationTest cases
passed, including 5-to-6 preserving online history/notes and cascading only local
positions.

Verification commands:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*Epub*Test' --tests '*HomeHistoryTest' --tests '*ReaderEntrySourceTest' --tests '*ProgressPersistenceTest' --tests '*BookmarkSnapshotWriterTest' --offline --max-workers=2 --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --max-workers=2 --console=plain
adb -s emulator-5554 shell am instrument -w -r -e class com.novelnest.app.data.local.epub.EpubImportIntegrationTest,com.novelnest.app.data.local.epub.EpubLifecycleTest,com.novelnest.app.data.database.MigrationTest com.novelnest.app.test/androidx.test.runner.AndroidJUnitRunner
~~~

Local diagnostic logs are under build/diagnostics/epub (ignored build output).
The before-fix resource diagnostic records the missing policy;
audit-instrumentation-fixed.log records the passing device run.

Remaining release checks: physical-device document providers, large real books,
long-session heap/CPU profiling, accessibility, process death, low storage,
unresponsive provider cancellation, and backup/device-transfer restoration.
The tested paths do not establish zero crashes or leaks for every publication or
OEM. In particular, Android may terminate the process, and a stalled document
provider is outside this repository's direct control.

References:
- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/training/data-storage/app-specific
- https://www.w3.org/TR/epub-33/
- https://github.com/readium/kotlin-toolkit/tree/3.0.3 (BSD-3-Clause)
