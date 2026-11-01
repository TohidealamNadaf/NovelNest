# Reader auto scroll

Reader -> TT quick settings -> Auto Scroll. Choose 0.25x-3x speed, then Start/Resume.
The sheet dismisses before the chapter begins moving. 1x means 20 dp/second, not words/minute.
The speed is saved; running intent is never stored in preferences or SavedStateHandle.

## Ownership and performance

`ReaderAutoScrollController` holds session intent and a generation ID in the reader ViewModel.
`ReaderAutoScrollEffect` owns one normal-priority LazyListState scroll mutation and waits on the Compose
frame clock. Fractional movement uses elapsed frame time and density; elapsed time is capped at 50 ms
to prevent catch-up jumps. Speed changes do not restart the runner. No timers, background services,
network calls, wake locks, per-frame preferences writes or chapter-wide measurement are added.

Only the current chapter/content version may run. The outgoing AnimatedContent chapter is ineligible.
The runner starts after scroll restoration, while the destination lifecycle is RESUMED and its window is focused, with the sheet
and chapter drawer closed. Manual touch pauses without consuming input. The central-tap recognizer
still opens quick settings when that touch interrupts auto scroll. Fast-scroller drags also pause.
Scroll mutation cancellation from a competing gesture is handled; owner cancellation still propagates.
Completion/interruption callbacks carry a run ID, so an older runner cannot pause or finish a newer one.

`ReaderScrollCheckpoint` keeps the latest authorized position in memory and limits SavedStateHandle
checkpoints during movement to once per 250 ms. Pause/Stop, navigation, content changes, lifecycle exit
and scroll observer disposal flush the final pending checkpoint. Original and rewrite keys remain separate.
Scroll positions continue using the existing SavedStateHandle behavior; this is not a new permanent
per-chapter position database.

## Behavior

| Event | Result |
|---|---|
| Touch chapter/header, swipe, drag fast scroller | Pause; touch is delivered to its normal handler |
| Open quick sheet or drawer | Pause; closing does not automatically resume |
| Start from quick sheet | Hide first, then start current chapter; cancelled dismissal drops Start |
| Chapter end | Finish; no automatic next chapter, overscroll, pull haptic or network fetch |
| Chapter change, refresh/retry, original/rewrite change | Stop |
| Text layout changes through quick settings | Pause |
| Lock, Home, leave reader, rotation | Pause and cancel frame work; explicit Resume required |
| Window loses focus (for example, another window covers it) | Pause; regaining focus does not resume |
| Process death/restart | Stopped; saved speed and existing reading-position restoration remain |
| Loading, empty/error chapter, configured Paged Flip mode | Start unavailable |

The existing Keep Screen On preference now controls the reader's view while RESUMED. Requests are
reference-counted across temporarily overlapping reader destinations; the previous view value is restored
after the final request is disposed. Disabling the preference permits normal screen timeout and lock
pauses auto scroll. No battery exemption or partial wake lock is requested. Audio narration remains
independent: auto scroll does not synchronize to audio passages or change its service/session.

Manual Start is an explicit request for movement, including when reduced decorative animations are
enabled. Controls include slower/faster buttons, semantic labels and minimum 48-dp touch targets.

Start/Resume from the main card and the Auto Scroll subpage share a single dismissal handoff.
RUNNING is published only after `hide()` completes and the sheet is removed, keeping the card's
phase-dependent content stable throughout that animation. Explicit hide disables sheet dragging;
repeated Start taps do not launch another animation. Cancellation/disposal or a changed
source/novel/chapter/content version invalidates the pending request. This is sheet-local transient
intent, not a persisted running flag. The existing lifecycle/focus/list-restoration gates still
control when the reader's single frame loop may move.

## Verification

Unit coverage: duplicate Start, stale callbacks, chapter/source/novel/content invalidation, frame timing
at 60/90/120 Hz, fractional movement, stalled-frame limits, speed validation and checkpoint batching/flush.
Verified 26 October 2026: `assembleDebug`, `assembleDebugAndroidTest` and the focused offline reader/AI/study
suite succeeded with `--offline --warning-mode fail --max-workers=1` using isolated build outputs.
Results: 175 unit tests, 174 passed, one opt-in live-provider test skipped, no failures/errors.
Five new Compose device tests compiled (speed/pause, gesture interruption, eligibility/completion,
quick-sheet controls and overlapping screen-on requests) but were not run: no device was connected.
Device checks must exercise real gesture interruption, sheet dismissal, drawer, end of chapter, rotation,
lock/unlock, text reflow, all themes and concurrent narration. Measure frame times and temperature during
sustained reading before making performance claims; rendering and an active display still consume power.

27 October 2026: after deferring Start until sheet dismissal completes, `assembleDebug` and
`assembleDebugAndroidTest` passed again with the same offline/isolated settings (29 seconds,
69 tasks; no Kotlin warnings reported). Tests were not executed for this follow-up change.
`adb devices` listed no connected phone, so Start -> touch to pause -> reopen -> Resume/Stop
and rapid main-menu flings still require on-device confirmation.
