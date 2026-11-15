# EPUB Library and series organization

## UI and navigation

Home's EPUB letter button opens epub-library. A series opens epub-collection/{collectionId}.
These are secondary pages with Back navigation; the existing three footer destinations
and selected website source remain unchanged. Their lists account for system navigation
insets and the shared narration mini-player. All reader opens still go through
EpubReaderActivity.open(context, bookId); group names and group IDs never enter reader routes.

The library reuses Home's section spacing and three-column card widths:

- Continue: latest read EPUB, with series/volume context, chapter, and that volume's percentage.
- Recently Read: ten distinct shelf entries; a series occupies one slot and opens its last-read volume.
- My Library: series folders and standalone EPUBs, read entries first and unread entries by import time.
- Series page: its Continue card and its volumes in saved reading order.

Header search is local. A matching volume keeps its parent series as one root result.
Opening that series lets the user search individual volumes. Long-press a card for
organization. Existing imports are initially standalone. All imported books are saved;
there is no separate website-style bookmark requirement.

Series grouping is explicit and flat. Names/volume labels are editable display text;
UUID series IDs and SHA-256 book IDs are stable identities. Import selection order is
the initial volume order, adjustable through Reorder volumes. No automatic title-based
merges or volume-to-volume auto-reading are performed.

## Persistence and consistency

Room version 7 introduces epub_collections (id, name, unique normalized nameKey,
createdAt) and nullable collectionId/volumeLabel plus volumeOrder on local_books.
The registered 6-to-7 migration preserves existing rows/locators and creates an index
on collectionId. The foreign key uses ON DELETE SET NULL. No destructive fallback is enabled.

LocalBookDao.librarySnapshot reads books and series in one transaction. Invalidation
observes books, positions and collections. Its count value is only a change signal:
it must not use distinctUntilChanged, since position/rename/order updates often keep
row counts equal. Listing queries project out book descriptions and full locator JSON;
only EpubPositionPreview enters library state. The reader loads the complete locator
separately by book ID. The generated relation query selects bookId, progression,
chapterTitle and updatedAt, not locatorJson.

Mutations run through EpubRepository on its IO dispatcher and coordinate with the
existing brief write lock. Room transactions validate move destinations, assign order,
and reject stale reorder requests rather than dropping newly imported volumes. The UI
can reload an order changed by a concurrent import. Series removal never deletes files
or positions. Book removal retains the existing explicit confirmation/journal behavior.

Files stay in files/epub_library/<bookId>/book.epub and cover.jpg. Organization does not
move/copy files, rehash EPUBs or open publications. No online source/database table or
reader algorithm is repurposed for local series.

## Imports, lifecycle and failures

One application-owned queue processes at most 100 selected URIs sequentially. Each
file uses existing bounded copying, archive validation, metadata/cover extraction and
publication closing. The destination ID is captured when the picker launches; changing
pages cannot retarget an active batch. Invalid files are reported while later files
continue. No automatic retry loop is added.

Duplicate content reuses the existing SHA-256 book ID, repairs missing private files
as before, and preserves its locator, label and collection. Importing a duplicate into
another series does not silently move it; the result directs the user to Move.
If a destination disappears after parsing starts, the new book is saved standalone
with a notice. A nonexistent destination detected before a batch starts rejects it.

New metadata and membership commit in one Room transaction. The existing file/Room
journal covers the filesystem boundary. Cancellation waits for cleanup before another
batch can start. Already committed books remain. Process death interrupts the queue;
startup recovers unfinished staging. Unprocessed URIs must be selected again; their
access grants and queue are not retained for automatic replay. No new storage permission,
persistent URI grant, foreground service or cloud request is introduced.

EpubLibraryViewModel derives grouping/search off the main thread, shares metadata
through lifecycle-aware flows and retains the query in SavedStateHandle. Lazy grids
and stable IDs preserve efficient rendering. Covers use the existing bounded local
thumbnail files and Coil caches, including a visible missing-cover fallback.
Operations prevent duplicate submissions, expose failures, and keep dialog drafts and
consumed success revisions through saved-state restoration. Loading, empty search,
missing series, interrupted import, and retryable library failures have explicit UI states.

## Verification

The initial library implementation, before the multi-selection/reader UI review below, was verified on 2026-11-14:

- Offline debug app and Android test APK assembly succeeded.
- 76 focused unit tests passed, including EPUB safety, Home source isolation, reading
  progress, reader startup/stability and manhwa regression checks.
- 28 Android tests passed on the Pixel 8 API 36 emulator, including real Readium
  rendering, lifecycle restoration, database migrations and complete Home -> library
  -> series -> reader -> Back navigation with the selected website source preserved.
- Final library screenshot reviewed; Git whitespace checks passed.


Focused unit tests cover grouping, resume selection, limits, volume order, search,
empty/deleted series, percentage validation, source isolation and existing EPUB safety.
Android checks cover migration chains, retained IDs/positions/files, live invalidation
with unchanged counts, stale ordering, mixed valid/invalid batches, duplicates,
cancellation, deleted destinations, UI organization and saved dialog drafts.
Live provider grants, large real libraries and physical-device performance remain
release verification work; no claim of zero memory use or zero device heat is made.


## Multi-selection and reader UI review (2026-11-14)

The EPUB Library and series headers use a centered app bar with no subtitle. The
existing source headers and footer navigation are unchanged.

Long-press enters selection mode in either the library grid or Recently Read row.
Tapping another card toggles its selection, and Select all selects items matching
the current filter. Clear selection/Back leaves selection mode before navigating.
Selection is stored as stable IDs in SavedStateHandle, reconciled against live
metadata, and bounded to 500 items to keep saved activity state small.

The selected-item action menu provides:

- Move selected EPUBs: appends books to a series or moves them to the standalone
  library. Folder nesting is unsupported; folder selections offer removal instead.
- Edit selected item: existing rename, label, order and individual actions, enabled
  for one item because their inputs are specific to that item.
- Remove selected items: explicit confirmation counts selected books and folders.
  Selected book copies/positions are deleted; selected folders keep their unselected
  volumes and positions. Original documents are never deleted.

The DAO validates the entire selection and destination before a transactional batch
move. Metadata removal also uses one transaction. Private file cleanup retains the
existing per-book deletion journals; OS interruption can leave journaled members
for recovery. File preparation/cleanup cannot be atomic with Room. Retrying after an
interrupted operation should use a fresh library selection. No new schema, storage
permission, network permission or dependency is required.

Import EPUBs uses OpenMultipleDocuments on both pages, with up to 100 files handled
sequentially by the existing application-owned queue. Multi-selection availability
inside the picker depends on the document provider. The destination is captured when
launching the picker. Completion/error cards now stay visible until dismissed instead
of relying on a brief Toast; successful files survive failure/cancellation of others.

The reader review corrected these functional issues without adding placeholder tools:

- Bottom controls previously overlaid the Readium viewport. They now have reserved
  space and a scrollable height limit on short screens, so the last lines remain reachable.
- A single control transition replaces two simultaneously measured panels. It has no
  animated viewport resizing, which would repeatedly repaginate WebView content.
- Navigator location collection follows the fragment's STARTED lifecycle. The existing
  pause/exit checkpoint and publication ownership are retained.
- Preference submission is keyed to the Readium settings actually used. Changes to
  unrelated web-reader options no longer trigger layout work in the EPUB navigator.
- Contents matching compares publication URLs and available anchors/titles rather
  than ambiguous path suffixes. A failed chapter jump leaves the drawer open.
- The drawer no longer labels all earlier sections as read after a jump: the current
  persistence stores a volume locator, not proof of completion for each chapter.
- Missing aggregate progress preserves the existing resume behavior; visible library
  progress is clamped to a finite fraction. Existing bounded cover/markup caches and
  offline EPUB resource restrictions are retained.

Placeholder bookmark/tools/immersive controls and the progress slider have not gained
new functionality. This revision was checked by source review and debug assembly;
automated suites and physical-device interaction tests have not been rerun for it.
The existing long-press UI check was updated to include the selected-item action-menu
step, but it has not been rerun for this revision. Contents lookup is keyed only to
resource URL/title changes, not every scroll locator, so scrolling does not repeatedly
scan a long table of contents.

References: [Android lifecycle recommendations](https://developer.android.com/topic/architecture/recommendations),
[Readium preference submission](https://readium.org/kotlin-toolkit/latest/guides/navigator/preferences/).


### Reader tap and system-bar correction

The reader starts with its large control panel closed. Unhandled reading-content taps
open/close it through Readium's InputListener; there is no Compose touch overlay on the
WebView. Readium handles selections and interactive links before forwarding such taps.
The listener is removed when its navigator leaves composition, ignores background and
open drawer/preferences states, and Back closes the expanded panel before leaving.
The existing compact footer and explicit More control remain available.

The EPUB reader now owns its edge-to-edge window styling. NovelNestTheme has a default-on
manageSystemBars parameter; only this reader opts out, avoiding competing window color
writes while preserving other screens. Header padding is inside its painted surface,
and the control panel's background includes navigation-bar space. Its navigation/cutout
padding sits outside the control scroller so buttons cannot scroll behind system UI.
Android's three-button navigation contrast scrim is disabled for this reader window;
transparent bars and light icons sit over the existing dark reader controls.

Debug assembly and whitespace checks passed for this correction. Physical-device tap,
gesture/three-button navigation and rotation checks have not been performed in this turn.
Guidance: [Android edge-to-edge setup](https://developer.android.com/develop/ui/compose/system/setup-e2e).


### Selection-only long-press and safe series removal

Long-press now calls an explicitly named, idempotent selectItem action in both library
and series cards (including Recently Read). It clears stale option state and never
opens an option modal. Follow-up taps read the ViewModel's current selection to avoid
opening a reader if input arrives before Compose recomposes. Move is available directly
in the selection toolbar; editing/confirmation dialogs require an explicit action.
Selection lookup is indexed by stable ID, and batch dialogs use a key set rather than
repeated list membership scans for large selections.

Inside a series, Selected item actions -> Remove from series confirms detaching the
selected volumes to the standalone library. One Room transaction checks that every
book still belongs to that series before changing membership. Book rows, EPUB files,
volume labels and reading-position rows are retained. A separate action explicitly
says Delete selected EPUBs from device for actual file/position deletion.

Both the current library/series importer and the retained legacy library dialog now
use EpubImportContract. It delegates result/ClipData parsing to AndroidX
OpenMultipleDocuments and explicitly requests EXTRA_ALLOW_MULTIPLE with CATEGORY_OPENABLE.
The picker can return one or many documents; the existing sequential queue accepts up
to 100 files. Long-press the first file in Android's file picker to enter its multiple
selection mode. Third-party providers may expose a different selection UI or support
only one file; the app cannot force a provider to implement multiple selection.

This correction passed debug assembly and whitespace review. Phone/picker interaction
tests and automated suites have not been run for this correction. The prior modal
report could not be reproduced from the checked active long-press routing; it already
used selection, and the legacy single-document importer was not an active navigation
route. Install the newly built APK when checking the updated behavior.
