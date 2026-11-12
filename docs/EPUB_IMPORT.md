# Local EPUB library

## Using it

Home header **+** opens Local Books. Import EPUB invokes Android's document picker.
The picker also accepts generic ZIP/binary MIME labels for providers that mislabel
EPUBs; validation still requires a real EPUB.
Select a DRM-free, reflowable EPUB 2 or EPUB 3 publication (maximum compressed file
size 100 MiB). Import progress, cancellation, success, duplicates and errors appear
in the modal. Tap an imported book to read; Delete asks for confirmation.

The original document is never changed. The modal lists books by most recent
reading/import time. An EPUB read more recently than the selected source's latest
online reading appears in Home's Continue card. Website tabs/ordinals are unchanged.

## Architecture

- Readium shared/streamer/navigator 3.0.3, pinned for Kotlin 2.0.21 / SDK 35
  compatibility; only EpubParser is used. No PDF, DRM, audiobook or OPDS adapter. The BSD license notice is included in
  assets/licenses/readium.txt.
- EpubRepository owns one application-scope import, bounded streaming copy/SHA-256,
  archive validation, metadata/thumbnail extraction, and durable storage.
- Room version 6 adds local_books and epub_positions with an additive 5-to-6
  migration. Existing website, notes, terms and manhwa progress tables are retained.
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
modal dismissal while the process lives. Process death interrupts import; next
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

## Verification

36 focused offline unit tests passed. Both debug APKs compiled and packaged. Device
instrumentation tests are compiled but not executed: no phone was connected, and
the configured Pixel 8 AVD has no installed system image. Physical-device rendering,
gestures, import-provider behavior and lifecycle checks remain release checks.

Offline tests: EpubArchiveValidatorTest, EpubContentSanitizerTest,
HomeEpubContinueTest, plus existing HomeHistoryTest, ReaderEntrySourceTest,
ProgressPersistenceTest and BookmarkSnapshotWriterTest.

Device tests: EpubImportIntegrationTest uses real Readium + Room against generated
EPUB 2/3 fixtures: metadata, spine order independent of ZIP/manifest order, TOC
fragments, safe resource access, duplicate import, retained progress, missing-file
repair, independence from the original, and deletion. MigrationTest verifies 5-to-6
preserves online reading history and notes and cascades only local positions.

Before release: run device tests and manually exercise document providers, a large
real book, embedded images, keyboard/accessibility, large text, rotation/background,
process death, low storage, import cancellation, missing files and restored backups.

References:
- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/training/data-storage/app-specific
- https://www.w3.org/TR/epub-33/
- https://github.com/readium/kotlin-toolkit/tree/3.0.3 (BSD-3-Clause)
