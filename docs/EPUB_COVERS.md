# EPUB covers and generated fallback

## Resolution order

1. The cover link identified by Readium from EPUB metadata.
2. Up to six local cover-labelled resource candidates, including the declared
   cover. Filename matching uses the basename, so an arbitrary image inside a
   folder named 'cover' is not automatically considered cover artwork.
3. Up to the first three reading-order sections. A labelled cover page may provide
   one raster image through HTML or an SVG wrapper. Only the very first unlabelled
   section can qualify as an image-only cover, with short text, exactly one image,
   and a substantial portrait image (at least 160x240; width/height 0.45..0.95).
4. The shared metadata cover displays the title, author and available volume label.
   A deterministic six-color palette uses the collection ID for series volumes,
   or the immutable book content ID for a standalone book. Rename/reorder does not
   randomize colors. Moving a book into a series adopts that series' palette.

The existing card geometry, selection, progress badges and interactions stay in
place. Empty series keep their folder icon. Actual embedded covers take priority
and fill the same image area. Generated covers are Compose text and remembered
brushes, not generated bitmaps or an AI/network operation.

## Ownership and limits

EpubCoverExtractor runs during import/reimport on IO. It uses Readium publication
resources and the existing Jsoup parser, without opening a WebView or renderer.
Only local resources are accepted; wrapper images must resolve to publication
manifest entries. Remote addresses and traversal outside the archive are rejected.
Small base64 raster images inside a bounded cover wrapper are also supported.

Limits are centralized in EpubCoverExtractor.kt:

- Six declared/named candidates and three front sections.
- 256 KiB wrapper parse limit, 12 MiB encoded raster limit, 24 MiB total accepted
  resource bytes per extraction.
- Original images at most 64 megapixels and 32,000 pixels per side.
- Raster sampling targets the existing 720x1000 thumbnail bounds before decode.
- Transparent raster covers are flattened against white for JPEG storage.
- JPEG quality 85; streams close and temporary/decoded bitmaps are released on
  success, failure and cancellation.

HTML resources already pass through EpubMarkupResource and the 4 MiB archive
markup limit. Its read is performed once before the stricter wrapper parse check;
length() is avoided because it would sanitize and allocate that document twice.
The 24 MiB budget counts accepted encoded resources, not all transient parser
allocations. There is no publication-wide image cache or first-page screenshot.

Pure vector SVG artwork and ambiguous multi-image cover pages use the metadata
cover. No heuristic walks the whole book looking for an illustration. A single
unlabelled first-page portrait is still a heuristic, not proof of publisher intent.
Cover discovery/decoding failure does not reject an otherwise readable EPUB.

## Existing books and cache refresh

Existing coverless books immediately use the metadata-based fallback in the UI.
Reimport the same original EPUB to attempt recovery of a missing/damaged saved
thumbnail. The content hash keeps this a duplicate, preserving book identity,
reading position, notes, collection membership and volume order.

A recovered thumbnail is staged, then published under the existing write mutex
only if the database book still exists. Atomic replacement is preferred, with a
normal same-filesystem move fallback. A thumbnail publish failure leaves the
book usable. Deleting a book during recovery cannot resurrect it.

observeCover exposes a lifecycle-collected stream of small file snapshots. Only
file metadata is inspected on IO while browsing. A cover revision invalidates
those snapshots after repair; unchanged files are suppressed with
distinctUntilChanged. Coil request identity includes book ID, modification time
and byte count, so a repaired file can replace an earlier failed image request.
No EPUB parsing, bitmap allocation or file-stat loop runs in card composition.
The continue-card ambient image and its thumbnail use the same request helper.

There is no automatic background library sweep. Reimport is the explicit repair
path for existing books. No database migration, permission, network service,
new dependency or EPUB-file edit is introduced.

## Readium decision

The project uses Readium 3.0.3. CoverService provides cover() and coverFitting(),
and supports a custom cover strategy. Its default coverFitting implementation
gets the cover bitmap before scaling it. The importer therefore retains bounded
resource reading and pre-decode sampling instead of replacing it with a full-size
bitmap call. The synthetic title-based cover is our application behavior.

- [Readium cover API](https://github.com/readium/kotlin-toolkit/blob/develop/docs/guides/getting-started.md)
- [CoverService](https://readium.org/kotlin-toolkit/3.4.0/api/readium/readium-shared/org.readium.r2.shared.publication.services/-cover-service/index.html)
- [Efficient bitmap loading](https://developer.android.com/topic/performance/graphics/load-bitmap)

## Verification

Artifacts: build/diagnostics/epub-cover-fallback/. Final offline :app:assembleDebug
succeeded in 1m 45s with no Kotlin warnings. Scoped diff/whitespace review passed.
No automated tests were added/run and no physical-device rendering/import
scenarios have been verified.

Device follow-up: declared raster cover; missing metadata with a named cover;
HTML/SVG image wrapper; coverless book; multiple images/short-text first page;
unsupported vector image; oversized/corrupt image; transparent PNG; cancellation;
low storage; reimport with progress/notes/series; removal during reimport; repaired
thumbnail appearing in Library, Series and Continue; palette stability after
restart, rename and volume reorder; light/dark UI and narrow cards.
