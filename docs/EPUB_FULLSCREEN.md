# EPUB reading controls: auto-hide and fullscreen

## Independent controls

| Control | Behavior |
| --- | --- |
| Auto-hide controls (formerly Immersive Mode) | Hides the app header/footer when the user resumes scrolling or swiping the EPUB. Android system bars stay visible unless Fullscreen is also enabled. |
| Fullscreen | Immediately hides app controls and Android system bars. The action changes to Exit fullscreen while active. |

Enabling Auto-hide closes quick settings but initially leaves the header/footer available. A deliberate reading drag of at least 12 dp hides them. Tap unhandled reading space to reveal the header and quick settings again. Closing quick settings leaves auto-hide chrome available until reading resumes. With Auto-hide disabled and Fullscreen off, the normal header/footer stay visible.

The two choices do not change each other. Exiting Fullscreen restores Android bars; if Auto-hide was selected, it remains enabled and app controls are revealed until the next reading gesture. While Fullscreen stays enabled, closing quick settings hides fullscreen chrome even if Auto-hide is off.

Back closes a submenu/settings sheet or chapter drawer first. With controls closed, Back exits Fullscreen. In Auto-hide mode with app controls hidden, Back reveals the header/footer first; another Back uses normal reader navigation. This also provides access on image-only sections whose image taps open the image viewer.

Links, selection and image taps retain existing Readium/image-viewer behavior. Scrolling inside settings, dragging a slider, opening the chapter drawer, programmatic locator/reflow updates and background work do not trigger auto-hide. Android edge gestures can reveal transient bars during fullscreen. Desktop/multi-window caption bars can remain visible by platform policy.

## State and performance

EpubReaderViewModel.chromeState is one immutable in-memory session state: fullscreen, autoHideControls and autoHideControlsRevealed. It survives rotation with the retained ViewModel. A new reader process/session starts with both modes off. No novel reader preference, database schema, EPUB file, cache or saved locator is rewritten by a display-mode selection.

Readium's existing InputListener receives unhandled reading drags on the UI thread. A 12-dp displacement threshold ignores touch jitter. The listener handles at most one hide action per gesture and always returns false, preserving Readium's normal scrolling/paged navigation. It is detached when its navigator is replaced or the composition is disposed. No timer, gesture interception, locator-based scroll inference, background loop or per-frame Compose progress update is added.

Normal mode reserves header/footer space. Auto-hide and fullscreen use the existing Ready viewport with positioned chrome overlays. Entering/leaving these modes can reflow the viewport once, while showing/hiding controls within the selected mode keeps the measured reader size stable. The existing navigator fragment and AndroidView remain at the same composition call site; mode changes do not reopen the Publication, recreate the fragment or submit new reading preferences.

Auto-hide-only reading reserves stable Android bar/cutout insets. Fullscreen reading protects cutouts/caption controls while allowing transient system bars to overlay it. Overlay chrome uses stable inset sizes, preventing bar visibility changes from driving a measurement/visibility feedback loop.

EpubReaderSystemBars applies state on display changes, lifecycle resume and window focus return. Listeners are removed on disposal. Activity-window updates are suspended while the image dialog manages its separate window and are reapplied afterward. Bar icon contrast follows the reading theme when controls are hidden and the dark control surface when chrome is visible. Reduced motion removes chrome transition duration. Repeated button actions during a closing sheet animation are ignored.

Android reference: [Hide system bars for immersive mode](https://developer.android.com/develop/ui/views/layout/immersive).

## Verification and device follow-up

Kotlin compilation, debug assembly and diff review are performed during implementation. No automated tests or physical-device gesture tests were requested for this change.

Before release, check continuous and paged EPUBs, all four combinations of the two switches/modes, enabling/disabling during reading, rapid button presses, scrolling outside/inside quick settings, tap/Back from submenus, drawer navigation, selection, image-only pages and viewer return, rotation, background/lock-screen return, light/dark themes, gesture and three-button navigation, narrow/landscape layouts, reduced motion and multi-window mode. Confirm that revealing/hiding chrome within an overlay mode does not repaginate the reader and that Readium's existing locator tracking continues.
