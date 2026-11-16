# EPUB chapter progress and seeking

## Cause

The control sheet rendered a Material Slider with an empty onValueChange callback.
Its thumb was fed a rounded integer chapter percentage, so dragging could neither
preview a new position nor navigate there. The reported chapter percentage also
fell back to the saved whole-book percentage when resource progression was absent.
Those percentages describe different ranges and cannot be substituted.

## Implementation

- EpubReaderActivity keeps one stable locator State holder and updates it through
  the existing lifecycle-owned currentLocator subscription. Only the progress
  control reads that holder; precise progress updates do not invalidate the whole
  reader tree. Existing checkpoint, chapter-return, footer and book percentage
  logic retain their ownership.
- EpubChapterProgressControl renders the existing progress header/slider layout.
  It uses locations.progression for the current spine resource, at full precision.
  Unknown or invalid values disable seeking instead of substituting book progress.
- Drag/tap changes update a local preview immediately. onValueChangeFinished
  submits one locator on release, rather than navigating on every pointer update.
  Native Slider semantics support accessibility adjustment; no custom drag detector
  competes with the sheet's vertical scrolling. Native drag cancellation clears
  an uncommitted preview.
- A fresh progression-only Locator retains the exact current resource URL and
  media type. Its HTML fragment, prior text selector, absolute position and total
  progression are omitted so they cannot override the requested percentage.
- Navigation validates the expected resource identity, finite 0..1 range,
  publication reading order, reader lifecycle, and visible controls context.
  A stale gesture cannot seek a newly opened chapter. The existing explicitJump
  guard cancels pending chapter-return correction before this intentional seek.
- Successful requests briefly retain their preview until Readium reports a new
  locator. One bounded snapshotFlow wait (maximum 2.5 seconds) handles clamped
  endpoints or short resources that emit no changed locator. New gestures, resource
  changes, disabled controls or disposal cancel/replace that wait. There is no
  polling, repeated seek, per-frame checkpoint write or durable synthetic position.
- If navigation is rejected, the preview clears and the reader's existing error
  toast explains the failure. Actual navigator reports remain the source of truth
  for saving and restoring reading position.

No network call, dependency, permission, database migration, EPUB modification or
webnovel/manhwa change is introduced. Existing colors, spacing and controls stay
in place. Fullscreen and Auto-hide behavior is retained.

## Scope and edge cases

Readium defines progression within a reading-order resource, usually one chapter.
Several TOC anchors may share one XHTML file; this slider covers that resource,
not a DOM-derived range between anchors. In paginated mode Readium snaps a seek
to a valid page. An endpoint can therefore settle below 100% because the locator
reports the start of the last viewport/page. One-screen resources may stay at 0%.
Do not fabricate 100% or mark a book complete from the requested thumb value.

Fixed-layout EPUB pages have no within-page reading progression, so their chapter
slider is disabled; their existing Previous/Next navigation remains available.
The slider is also disabled while missing valid progression, a navigator, or the
visible controls context. A closing/hidden sheet cannot commit a late seek.

## Verification and device follow-up

Final offline :app:assembleDebug passed in 28 seconds with no Kotlin warnings.
Scoped diff and whitespace review passed. Build/review artifacts:
build/diagnostics/epub-chapter-progress/.
No automated tests are added/run and physical-device behavior is not yet verified.

Check continuous and paginated books at 0%, 25%, 75% and 100%; short/image-only
resources; cancelled drags and vertical sheet scrolling; rapid separate seeks;
chapter changes during a gesture; TOC return points after a seek; close/reopen;
rotation/backgrounding; fullscreen/auto-hide; TalkBack adjustment; missing resource
failures; and fixed-layout books. Confirm the saved position follows Readium's
actual report rather than a requested fraction.

## References

- [Compose Slider](https://developer.android.com/develop/ui/compose/components/slider)
- [Readium locator semantics](https://readium.org/architecture/models/locators/)
- [Pinned Readium 3.0.3 navigator](https://github.com/readium/kotlin-toolkit/blob/3.0.3/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubNavigatorFragment.kt)
