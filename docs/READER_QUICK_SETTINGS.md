# Reader Quick Settings fixes — 26 October 2026

The reader uses Material 3 1.4.0 for its supported `sheetGesturesEnabled` switch.
The version catalog pins this stable release above the existing Compose BOM's older Material version;
its declared minimum Compose dependencies also resolve through Gradle. No database, permission
or AI-provider configuration change is required. Existing app layouts/themes are retained.

## Interaction ownership

- Material's independent window Back callback is disabled. `ReaderSheetBackHandler`
  registers with the dialog's own Back dispatcher. Back returns from a detail page
  to its list, from a list/Summary to the main menu, and then closes the menu.
  An open keyboard retains Android's normal first-Back behavior.
- Every non-main page disables sheet gestures at Material's own `ModalBottomSheet`.
  This removes both its nested-scroll connection and its enabled drag recognizer.
  Notes list, note preview/edit, Extract list/detail, Summary and Auto Scroll are
  covered by the same page policy, including headers, empty areas and inset padding.
  Gestures remain disabled until a transition back to MAIN finishes, so an outgoing
  subpage cannot be dragged during its exit animation.
- The earlier leftover-scroll absorber and root drag detector were removed. Those
  could not disable Material 1.3.1's separate drag recognizer, especially while an
  animation was running. Child lists, flings, text selection and the native editor
  now receive their normal input without a competing app-level drag blocker.
- The main menu retains Material's swipe dismissal, including on its decorative
  handle. The extra app-level handle drag detector was removed: it could request
  `hide()` while Material was also settling the same gesture. Explicit close requests
  are coalesced; a cancelled hide animation does not unconditionally remove a visible sheet.
- Dismissal notification is idempotent for each mounted sheet and is invalidated on
  disposal. Explicit close, Material dismissal and narration close use that same
  notification, so a late callback from an old sheet cannot close a newer instance.

## Animation stability (27 October 2026)

The previous Material update exposed an inset-ownership compatibility problem. In
Material 3 1.3.1, `BottomSheetDefaults.windowInsets` contained bottom insets only;
in 1.4.0 it contains both top and bottom. Material 1.4.0 also pre-consumes top inset
according to its animated sheet offset. Reading the new default inside the measured
content couples top padding to that offset. Near full height, content measurement
can change expanded anchors and restart the running animation, making it chase a
moving target. Main-menu native fling settlement and the custom handle's independent
hide request provided an additional animation-conflict path.

The sheet now reserves physical top safe area on its outer modifier, before the
anchor measurement, and explicitly applies bottom-only insets inside. The space
available for content is independent of the animated offset. Short sheets retain
their bottom alignment; tall/IME-constrained sheets respect the top safe area.
The widget layout, note/AI data flows, subpage gesture lock and Back/scrim rules remain.
Material is the single owner of Main's drag/fling gesture; there is no automatic show,
expand or retry loop added by the app.

These paths were established from app and exact-version AndroidX source, not from
a captured device trace. The user identified a fast swipe on MAIN and Start/stop/reopen
of Auto Scroll as triggers. No phone was connected to reproduce the reported symptom;
on-device confirmation remains outstanding.

## Auto Scroll dismissal handoff (27 October 2026)

Start/Resume previously published RUNNING before requesting `hide()`. The main card
then gained active controls, and the Auto Scroll subpage changed its status text,
while Material was measuring/animating the surface. The reader's eligibility gate
prevented actual background chapter movement, but did not prevent those sheet
content updates. Main's drag recognizer also remained enabled during explicit hide,
allowing a fast gesture to interrupt that animation.

Both entry points now share one deferred Start request. The sheet retains its current
phase while hiding, disables its own drag gestures during explicit dismissal, removes
itself through the existing one-shot callback, and only then starts Auto Scroll. A
cancelled hide, native dismissal or disposal discards the pending Start. The request
also checks source/novel/chapter/content identity and Continuous mode at completion,
so a changed chapter, rewrite version or loading state cannot start stale work.
Repeated Start taps coalesce into the in-flight hide; there is no timer, retry,
automatic re-open or additional scrolling coroutine. Opening the sheet still pauses
the existing run, and closing it without Start/Resume never resumes scrolling.

This removes a concrete content/animation conflict; it is not a device trace proving
that Auto Scroll alone caused the previously reported continuous shaking.

## Note edits

The body editor is a bounded native `EditText` inside a scrollable Compose form.
For overflowing text it claims the touch sequence on DOWN using Android's
`requestDisallowInterceptTouchEvent(true)`, so the form cannot intercept the drag
before the native editor scrolls. UP/CANCEL and detachment release that request.
Short text does not claim the gesture, leaving the form scrollable from that area;
title, toolbar and surrounding form areas retain their own scrolling behavior.
The existing editor dimensions, native movement/selection methods, Markdown actions,
IME handling and draft/save guards remain. This change adds no sheet/reader gesture
handler, scrolling coroutine or new animation.
Android's interception contract: https://developer.android.com/develop/ui/views/touch-and-input/gestures/viewgroup

### Keyboard and cursor visibility (27 October 2026)

The resolved Compose UI dependency is 1.8.2 (confirmed by Gradle dependency insight).
Its native `AndroidViewHolder` does not forward a child View's rectangle-on-screen
request into Compose's scroll responders. The native editor can therefore keep a
cursor visible in its own 170-280 dp bounds while the surrounding form clips that
line when the keyboard reduces the form's viewport. Material already applies
`imePadding()` in the sheet's dialog; adding another IME inset would duplicate it.

The editor now forwards native rectangle requests to a `BringIntoViewRequester`
attached to the existing `AndroidView`. Rectangles are converted from native content
coordinates to its viewport coordinates, including its current scroll offsets, and
clipped to its bounds. The form reveals the active line instead of trying to fit the
entire editor into a small keyboard-constrained viewport. A viewport size change
requests a fresh native cursor reveal after layout, covering keyboard opening/resizing.
TextView's normal cursor movement, internal scroll and selection behavior remain.

One conflated channel keeps only the latest rectangle; `collectLatest` cancels an
outdated reveal, and the effect waits for layout before scrolling. Requests are
checked against the currently attached, focused editor. Preview discards requests,
release clears the View callback/reference, detachment cancels the one pending native
frame callback, and disposal closes the channel/cancels the effect. There is no
polling, fixed delay, new sheet animation, additional padding, height change or UI
restructure. The subpage drag restriction and Auto Scroll handoff remain independent.

Native rectangle coordinates: https://developer.android.com/reference/android/view/View#requestRectangleOnScreen(android.graphics.Rect,boolean)
Exact Compose source: https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.8.2/ui-android-1.8.2-sources.jar

`ReaderNoteDraft` holds one bounded title/Markdown draft at sheet level. Editor
text changes update that object without writing to storage or publishing every
keystroke through the ViewModel. Its saver supports normal UI state restoration.
The existing 160-character title and 64,000-character body input limits remain.

Leaving a modified note through its Back button, system Back or a scrim tap asks
whether to keep editing or discard changes. Keeping the draft changes nothing;
discarding resets it to the stored note and completes the requested exit. An
in-flight note save blocks exit until the existing save acknowledgement/error
arrives. Clean pages still close from a scrim tap.

The hidden-state confirmation callback remains stable. It vetoes only dirty/in-flight note exits;
there is no blanket subpage veto that would disable outside-tap dismissal.
This is not an automatic draft database or a promise to recover edits after a
force-stop. Saved notes continue to use the existing repository and source/novel
identity checks.

## Extraction feedback

Detail progress, cancellation and failures require the current chapter and the
displayed collection ID to match the request. Another collection's running request
does not appear as work on the displayed collection. The list also ignores failures
for removed collections. Shared failure cards display the existing sanitized
message and retry only the owning collection through the existing callback.
No automatic generation retry or change to provider quotas was added.

## Verification

- Verified on 27 October 2026: `assembleDebug :app:assembleDebugAndroidTest --offline
  --warning-mode fail --max-workers=1` passed using isolated build outputs with Material 3 1.4.0.
  The same build command also passed after the inset/gesture/dismissal animation corrections.
  After the Auto Scroll dismissal handoff correction it passed again in 29 seconds
  (69 tasks; 7 executed, 62 up-to-date), with no Kotlin warnings reported.
  After the native note-editor scroll ownership correction, both APK builds passed
  in 39 seconds (69 tasks; 8 executed, 61 up-to-date), with no Kotlin warnings reported.
  Tests were not executed for that correction; long-note drags in both directions,
  short-note form scrolling, IME resize, selection/formatting and Save still need device checks.
  The subsequent native cursor-to-Compose visibility bridge also passed both APK
  builds (29 seconds; 69 tasks, 7 executed, 62 up-to-date), without Kotlin warnings.
  No tests were executed for that bridge. Verify typing/placing the cursor on the
  last line with the keyboard visible, IME resize/hide, manual text scrolling,
  selection/formatting and leaving/reopening Preview on a real device.
  No unit or device tests were executed for this gesture fix; no phone was connected.
  Previously recorded test results below describe the earlier build; device checks are still needed for
  scrollable content, header/footer drags, quick repeated flings, transitions, native
  note selection/editing, Back and outside-tap dismissal with the updated dependency.
  The root fix uses the official API: https://developer.android.com/reference/kotlin/androidx/compose/material3/ModalBottomSheet.composable
- `assembleDebug` and the reader/AI/study unit checks passed: 165 tests,
  164 passed, one opt-in live test skipped, no failures/errors.
- The two always-true null checks were removed. Kotlin compilation reported no
  warnings during the final app build.
- Six Android UI regression tests were added in `ReaderQuickSettingsSheetTest`:
  dirty-note scrim confirmation, hierarchical system Back, clean scrim dismissal,
  a footer-padding fling, note state restoration, and collection-specific feedback/retry.
  They are compiled into the test APK, but have not been run because no device is
  connected. No provider or narration request is made by these tests.
- Before release, run those UI tests and manually check drag transitions, native
  text selection, keyboard Back, rotation, and phone/OEM navigation modes. Compilation
  and host unit tests cannot establish touch behavior or frame-time performance.
