# EPUB reading controls: auto-hide and fullscreen

## Independent controls

| Control | Behavior |
| --- | --- |
| Auto-hide controls (formerly Immersive Mode) | Hides the app header/footer when the user resumes scrolling or swiping the EPUB. Android system bars stay visible unless Fullscreen is also enabled. |
| Fullscreen | Immediately hides app controls and Android system bars. The action changes to Exit fullscreen while active. |

Enabling Auto-hide closes quick settings but initially leaves the header/footer available. A deliberate reading drag of at least 12 dp hides them. When controls are hidden, the first tap on unhandled reading space only reveals the header/footer and consumes the tap. Once the reveal animation has finished, another reading-space tap or a visible settings/footer action can open quick settings. Closing quick settings leaves auto-hide chrome available until reading resumes. With Auto-hide disabled and Fullscreen off, the normal header/footer stay visible.

The two choices do not change each other. Exiting Fullscreen restores Android bars; if Auto-hide was selected, it remains enabled and app controls are revealed until the next reading gesture. Fullscreen uses the same two-step reveal behavior, including when Auto-hide is also enabled. Closing quick settings leaves revealed controls available; the next deliberate reading drag hides them again. Fullscreen remains enabled until explicitly exited.

Back closes a submenu/settings sheet or chapter drawer first. With controls closed, Back exits Fullscreen. In Auto-hide mode with app controls hidden, Back reveals the header/footer first; another Back uses normal reader navigation. This also provides access on image-only sections whose image taps open the image viewer.

Links, selection and image taps retain existing Readium/image-viewer behavior. Scrolling inside settings, dragging a slider, opening the chapter drawer, programmatic locator/reflow updates and background work do not trigger auto-hide. Android edge gestures can reveal transient bars during fullscreen. Desktop/multi-window caption bars can remain visible by platform policy.

## State and performance

EpubReaderViewModel.chromeState combines the persisted EPUB-only autoHideControls preference with session-local fullscreen and controlsRevealed flags. Auto-hide is loaded synchronously from ReaderPreferencesManager before the first frame and saved when its switch changes. It survives leaving/reopening the reader, app restart and opening another EPUB. Fullscreen starts off and controls start revealed in each new reader session. Reset reading settings restores auto-hide to its default (off); image-layout reset does not change it. A distinct preference subscription keeps an open reader in sync with resets without reacting to unrelated font/theme changes. No novel reader behavior, database schema, EPUB file, cache or saved locator is rewritten.

Readium's existing InputListener receives unhandled reading drags on the UI thread. A 12-dp displacement threshold ignores touch jitter. The listener handles at most one hide action per gesture and always returns false, preserving Readium's normal scrolling/paged navigation. It is detached when its navigator is replaced or the composition is disposed. No timer, gesture interception, locator-based scroll inference, background loop or per-frame Compose progress update is added.

Normal mode reserves header/footer space. Auto-hide and fullscreen use the existing Ready viewport with positioned chrome overlays. Entering/leaving these modes can reflow the viewport once, while showing/hiding controls within the selected mode keeps the measured reader size stable. The existing navigator fragment and AndroidView remain at the same composition call site; mode changes do not reopen the Publication, recreate the fragment or submit new reading preferences.

Auto-hide-only reading reserves stable Android bar/cutout insets. Fullscreen reading protects cutouts/caption controls while allowing transient system bars to overlay it. Overlay chrome uses stable inset sizes, preventing bar visibility changes from driving a measurement/visibility feedback loop.

EpubReaderSystemBars applies state on display changes, lifecycle resume and window focus return. Listeners are removed on disposal. Activity-window updates are suspended while the image dialog manages its separate window and are reapplied afterward. Bar icon contrast follows the reading theme when controls are hidden and the dark control surface when chrome is visible. Reduced motion removes chrome transition duration. Header/footer overlays share one Compose transition. Sheet-opening and chrome navigation actions check its actual settled-visible state and the latest session state, so entering/exiting controls cannot accept accidental actions or rapid taps before recomposition. No fixed-delay gate or additional timer is used. Repeated mode-button actions during a closing sheet animation are ignored.

Android reference: [Hide system bars for immersive mode](https://developer.android.com/develop/ui/views/layout/immersive).

## Verification and device follow-up

Kotlin compilation, debug assembly and diff review are performed during implementation. No automated tests or physical-device gesture tests were requested for this change.

Before release, check continuous and paged EPUBs, all four combinations of the two switches/modes, enabling/disabling during reading, rapid button presses, first-tap reveal without opening settings, taps during both overlay animation directions, subsequent settled taps, scrolling outside/inside quick settings, tap/Back from submenus, drawer navigation, selection, image-only pages and viewer return, rotation, background/lock-screen return, light/dark themes, gesture and three-button navigation, narrow/landscape layouts, reduced motion and multi-window mode. Confirm that revealing/hiding chrome within an overlay mode does not repaginate the reader and that Readium's existing locator tracking continues.


## Auto-hide activation flicker fix (16 November 2026)

The old transition target was false in normal mode even though the normal header
and footer were visible. Enabling Auto-hide removed those controls and introduced
new overlays driven from false to true, causing the visible header to disappear
and animate back in. The visibility transition now represents actual chrome
visibility: normal mode is already visible. Overlay nodes are composed only while
an overlay mode is active, so a normal-to-visible-overlay handoff has no enter
animation or duplicate chrome. Fullscreen hiding and genuine reading hide/reveal
still animate. The existing settled-visibility tap gate is retained.

This uses the existing shared Compose transition rather than a delay, animation
restart flag or second copy of the header. Android references:
[updateTransition](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/package-summary)
and [animation composables](https://developer.android.com/develop/ui/compose/animation/composables-modifiers).

Build/diff verification: build/diagnostics/reader-selection-and-chrome/. Runtime
checks still needed: repeated activation/deactivation, all Fullscreen/Auto-hide
combinations, reduced motion, rotation and both overlay animation directions.


## Auto-hide preference persistence (16 November 2026)

Cause: Auto-hide was initialized to false in each new EpubReaderViewModel and its
setter changed only session memory. Closing the reader destroyed that state.
The switch now saves the EPUB-only reader_epub_auto_hide_controls preference,
initial chrome state restores its current value synchronously, and a distinct
ViewModel-scoped subscription propagates preference resets. The subscription is
cancelled automatically when the ViewModel is cleared. Controls initially remain
revealed; normal reading gestures hide them using the existing behavior.

The new preference defaults to off for existing installations and new installs.
Turning it off persists too. Reading-settings reset writes that same default;
image-alignment reset leaves this preference alone. Fullscreen and current
header/footer reveal state remain transient. No publication reload, locator
rewrite, new cache, timer, database migration or reader UI redesign is added.

Verification: offline :app:assembleDebug succeeded in 1m 32s. Scoped diff and
whitespace review passed. The build reports three existing deprecated notification
Action.Builder warnings in NarrationService, outside this change. No automated
tests were added/run. Device follow-up: enable, exit/reopen, app restart, open a
different EPUB, disable/reopen, rotate while controls are hidden, and reset reading
settings. Artifacts: build/diagnostics/epub-autohide-persistence/.
