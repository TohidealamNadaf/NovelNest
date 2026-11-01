# Reader fast scrolling

The chapter reader overlays `ReaderFastScroller` beside its existing paragraph
`LazyColumn`. The list and its SavedStateHandle checkpoint remain the source of
reading position. No chapter repository, cache, narration, or read-status changes
are involved.

- The rightmost 48dp is a vertical drag target. Its narrow thumb is faint when
  idle and solid while dragging; a temporary approximate percentage is shown.
- Intermediate progress uses item position and visible-item height estimates.
  It is not an exact percentage of chapter pixels or words. No offscreen text
  measurement or chapter-sized height map is needed. Each gesture freezes its
  estimated item range to avoid changing the mapping as paragraphs enter view.
- Track endpoints jump to the first/last list item, including chapter padding and
  the existing end-of-chapter section. A gesture that grabs the thumb preserves
  its grab offset instead of snapping the thumb centre under the finger.
- One coroutine consumes a conflated target channel, applying at most one jump
  per display frame. Direct jumps avoid traversing every intermediate paragraph.
  Layout/drawing reads keep high-frequency position updates out of the reader's
  parent composition. There is no timer, background polling, network request,
  full-chapter measurement, or database write from the scroller.
- Dragging suppresses chapter pull gestures. Releasing at the bottom does not
  select the next chapter. Disposal cancels pending work and clears dragging.
  New gestures cannot be cleared by an old gesture finishing asynchronously.
- Empty/loading content, short content that fits the screen, outgoing chapter
  transitions, open/opening drawers, and reader settings disable the scroller.
- Header/bottom spacing respects the existing reader and narration layout.
  Accessibility exposes adjustable progress with an approximate-position label.
  The control adds no motion animation and supports reduced-motion settings.

Build verification does not establish device smoothness. Before release, check
long and short chapters, mixed paragraph sizes, font/spacing changes, rotation,
rapid drag reversal, repeated gestures, cancellation, TalkBack adjustment,
background restoration, narration layout, and both exact endpoints on a device.

Initial implementation verification used Kotlin compilation; the first APK
assembly was cancelled during resource-heavy dependency processing. The subsequent
system-design repair completed `:app:assembleDebug --offline --max-workers=1`,
including this component. The selected 138 offline regression tests passed, but
there are no automated fast-scroller interaction tests or physical-device results.
