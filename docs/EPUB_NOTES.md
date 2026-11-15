# EPUB notes

The EPUB reader's existing Note tool opens Book Notes inside the same bottom-sheet
surface. It reuses the novel reader's notes list, native Markdown editor, preview,
formatting actions, selection behavior, validation, and Room repository.

## Ownership and persistence

- Notes are stored under `source = LOCAL_EPUB`, the imported book's stable
  `epub-<sha256>` identity, and the reading resource href without its fragment.
- The list is book-wide: a note created in one chapter is visible in every chapter
  of that EPUB. Separate EPUB files/volumes have separate notes; moving a book
  between library series does not change its identity or its notes.
- Existing-note edits keep the original chapter association. Updates/deletes are
  checked against the source and book; other books and online-source notes cannot
  be edited through this reader.
- Notes are user-owned Room data, not EPUB markup, image/audio cache, or reading
  positions. Removing a local book does not erase its notes. Importing the exact
  same file restores the same identity and makes its notes accessible again.
- No schema migration, permission, AI request, or additional dependency is needed.

## UI and lifecycle

1. Open reader controls and tap Note.
2. Use + to create a note with a title and Markdown body; tap a saved note to
   preview it and Edit to change it. Deletion requires confirmation.
3. Saving waits for the repository transaction to succeed before returning to the
   list. Failed saves retain the draft and show the error in the editor.
4. Back, reader taps, header actions, and leaving the reader check unsaved edits.
   A pending save cannot be interrupted by these navigation actions. Auto-hide
   gestures and illustration taps cannot silently dismiss the notes editor.
5. The one bounded editor draft is retained in the reader ViewModel across
   configuration changes. It contains no Activity/View references or callbacks.
   Unsaved drafts are not durable across process death; explicitly saved notes are.
6. Notes Room subscriptions exist only while this page is visible and the Activity
   is STARTED. EPUB uses the shared controller's notes-only mode, so it does not
   subscribe to AI term collections. The novel reader retains its existing default.
7. The Notes pages own their internal scrolling. The outer EPUB sheet does not
   introduce another scroll owner, and IME padding applies to the notes overlay.
   Readium's publication, locator checkpoints, and navigation remain independent.

## Bounds and verification

The shared repository enforces titles of 1-160 characters, a 64,000-character body,
and at most 200 notes originating from one chapter. Lists load short previews; the
full body is loaded only for the selected note.

Implementation verification: see `build/diagnostics/epub-notes/build.log` for the
local offline debug assembly result. No automated tests or physical-device checks
were run as part of this change. Device checks should cover new/edit/delete,
cross-chapter visibility, separate-book/source isolation, keyboard selection and
last-line visibility, rotation during loading/saving, unsaved-edit confirmation,
fullscreen/auto-hide, and background/reopen behavior.
