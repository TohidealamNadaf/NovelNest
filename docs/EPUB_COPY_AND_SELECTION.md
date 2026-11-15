# EPUB chapter copy and selection scrolling

## Copy Chapter

The first action in the EPUB sheet's tools card is Copy (replaces the Brightness
placeholder). It reads the active spine resource once, off Main, under the same
publication-resource mutex used by the image viewer and publication shutdown.
Jsoup extracts body text, including paragraph/line breaks, without scripts, styles,
SVG image labels, or hidden content. No whole-book traversal or plaintext cache is
introduced. The action copies the current EPUB section, which can differ from a
logical chapter when the publisher splits/combines sections.

Only one copy can run. The action is disabled while busy. Backgrounding cancels it;
the clipboard is written only while the Activity is resumed and focused and the
same section remains active. A section change asks the user to tap Copy again.
Image-only sections, missing resources, memory pressure and oversized results show
an error. Text above 120,000 UTF-16 code units is rejected without truncation to
leave space below the shared Binder transaction limit. Android 13+ supplies its
own clipboard confirmation; older versions receive a toast. Existing EPUB files,
notes, progress checkpoints, and web-novel copy behavior are unchanged.

## Selection scrolling

Readium 3.0.3's R2BasicWebView.onOverScrolled returns while isSelecting is true.
That protection was added for paginated CSS columns, but also blocks continuous
selection-handle scrolling. EpubSelectionDocument injects a trusted, event-driven
hook after publication sanitization. Only selection state, renderer scroll Y, and
viewport width cross the section-scoped bridge; selected text is never transmitted.

EpubSelectionScrollController accepts events only for the current reading resource
and an attached, visible WebView with the matching local resource path. It keeps
one latest event and one queued Main callback, using a weak View reference. In
continuous mode it synchronizes the native vertical viewport to the renderer's
selection scroll, with bounded CSS-to-native scaling. Equal offsets are ignored
so synchronization cannot create a scroll feedback loop. Paged horizontal offsets,
Readium selection state and native handles remain untouched. There is no polling,
HTTP call, library upgrade, reflection, or synthetic pointer input.

DOM selection also bypasses the curved-scroll guard and reader chrome tap/drag
handlers. Locator changes clear old selection metadata; lifecycle pause drops
queued updates and destruction closes the controller. Notes and the separate
image viewer retain their own scrolling paths.

## Verification

Build output: build/diagnostics/epub-copy-selection/build.log. No automated tests
or physical-device gesture checks are included in this change. Confirm clipboard
content/paragraphs, image-only and large sections, repeated taps, backgrounding,
chapter changes, rotation, and selection handles at both viewport edges on a phone.
Also check curved vertical scrolling, deliberate chapter swipes, image taps, and
paged selection. Renderer/selection behavior can vary with Android System WebView;
the continuous-mode synchronization needs that device confirmation.

Sources: https://github.com/readium/kotlin-toolkit/issues/325 and
https://chromium.googlesource.com/chromium/src/+/main/android_webview/java/src/org/chromium/android_webview/AwScrollOffsetManager.java.
Clipboard behavior: https://developer.android.com/develop/ui/views/touch-and-input/copy-paste.

## Selected-text contrast

Readium 3.0.3 defaults to a light-blue selection background with selection text
set to inherit. Custom dark reader text therefore remains light over that light
highlight. EpubSelectionStyle explicitly supplies both Readium selection colors
and a matching paint-only selection rule in sanitized chapter markup. The opaque
amber #E2AA62 and dark text #171A1F have approximately 8.4:1 contrast, independent
of Obsidian, AMOLED, Slate, Parchment, or Paper backgrounds. Nested spans, links,
and publisher WebKit text-fill styles receive the selected-text foreground too.
There are no theme-change scripts, timers, new resource reads or layout changes.
The existing native selection handles, range, copying and scroll synchronization
retain their behavior. New and preloaded sections receive the same styles, and
switching reader themes does not require rebuilding the navigator.

Build output for this change: build/diagnostics/epub-selection-contrast/build.log.
No automated tests or phone checks were run. Confirm selection while dragging
both handles, across headings/links, and after theme changes in scroll/paged mode.
CSS selection reference: https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Selectors/::selection.
