# EPUB chapter return positions

## Reported behavior

In continuous scrolling, leaving a chapter and navigating backward could open it
midway instead of at the viewport where it was left. The app stored a single book
checkpoint and delegated adjacent chapter navigation to Readium 3.0.3. Readium's
resource navigation applies its own beginning/end and pager setup; the app had no
per-resource return locator to restore afterward.

## Implementation

- EpubChapterPositions keeps the last 128 visited reading-resource locators, using
  an access-ordered bounded map in EpubReaderViewModel. It retains only the href,
  media type, bounded title and within-resource progression, never chapter text,
  Views, HTML, screenshots, or Activity callbacks. It survives Activity rotation.
- Confirmed locator updates refresh this memory even when the existing two-second
  durable checkpoint throttle skips a database write.
- EpubChapterReturnController listens to Readium's public jump callbacks. For an
  adjacent resource jump in continuous mode, it posts one explicit saved locator
  after Readium finishes its synchronous resource-switch setup. Cached and newly
  loaded resources use the same public go(locator) API.
- Applying that locator cannot recursively trigger another restore. Intermediate
  destination entry points are excluded from the app's checkpoint/history updates.
- New jumps supersede pending callbacks. Leaving the Activity, changing scroll
  mode, or explicit TOC navigation cancels pending restoration. A single 2.5-second
  watchdog releases an unchanged/clamped destination that emits no new locator;
  failures show feedback instead of leaving a permanently pending state.
- Gesture ownership, text selection, paged navigation, explicit TOC/anchor jumps,
  novel reader state, and manhwa progress handling remain separate. The same
  listener covers continuous swipes and Previous/Next controls.

## Persistence boundary

Room still stores the latest overall book checkpoint for reopening the EPUB. The
bounded chapter-return history is a reader-session convenience, retained across
rotation; it is not a new permanent per-chapter database. Process death/new reader
sessions and chapters evicted from the 128-entry history use normal Readium entry
behavior. Unvisited chapters also retain that behavior. No migration is required.

## Verification

Offline :app:assembleDebug passed (20 seconds); see
build/diagnostics/epub-chapter-return/build.log. Diff checks were performed.
No automated tests or physical-device reproduction were run for this change.
Confirm the reported EPUB on the phone: leave a chapter from several offsets,
return by swipe/buttons, repeat after rotation, change scroll mode, use TOC anchors,
rapidly alternate directions, open illustrated sections, and background mid-turn.
Readium locators describe a relative viewport position; layout/font/image changes
can shift the exact text line at that position.

## Curved vertical scrolling

Readium 3.0.3's continuous-scroll touch handler can mark a gesture as horizontally
dragged after sideways drift. On release it checks a fixed 200-physical-pixel net
vertical displacement and the horizontal direction. This does not establish the
initial intent, and a curved vertical reading scroll can accidentally turn chapters.

EpubScrollGestureLayout wraps only the existing Readium container, with no visual
layout/padding changes. It classifies the earliest motion samples, including batched
history. A clearly vertical or ambiguous drag remains vertical until the finger is
lifted. Horizontal intent requires stronger directional dominance; chapter swipes
also need at least 48 dp of horizontal travel. Clear, sufficiently long horizontal
swipes continue through Readium's existing chapter navigation.

For a non-horizontal drag the guard forwards a pooled copy of ACTION_UP with X
matching the original DOWN X. Y, timestamps, velocity history on earlier movement
samples, and the native scroll/fling sequence remain unchanged. All other events
pass through directly. There is no touch interception, synthetic CANCEL, per-MOVE
allocation, JavaScript evaluation, polling, or replacement of WebView touch listeners.
The copied release event is recycled in finally.

Paged mode, buttons and TOC navigation bypass this continuous-only guard. Multi-touch,
non-touchscreen input, accessibility touch exploration, an active native text-selection
ActionMode, and gestures beginning as long presses also bypass filtering. The guard
is outside the Readium viewport; notes/editor and fullscreen image-viewer gestures
use their separate existing input paths.

Build log: build/diagnostics/epub-curved-scroll/build.log. No automated tests or
physical-phone gesture reproduction were run for this change. Check curved up/down
scrolls, vertical flings, ambiguous diagonals, intentional horizontal swipes, text
selection, image taps, and mode changes on the target phone before release.
