# Webnovel chapter text selection

## Why the old selector stopped at a paragraph

Each paragraph had its own SelectionContainer. Its selection registrar could not
see neighboring paragraphs. It also used only the default Compose toolbar, so the
private EPUB Define action never reached webnovel text.

Wrapping LazyColumn in a single SelectionContainer does not solve the full problem:
[Android documents undefined selection/copy behavior for uncomposed lazy items](https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/selection/SelectionContainer.composable).
This implementation preserves the current lazy list, paragraph keys, typography,
scroll checkpoint indexes and fast scroller instead of composing one giant chapter.
No dependency or database change is needed.

## State and rendering

- ReaderChapterSelection owns immutable chapter strings and a start/end pair of
  paragraph and UTF-16 offsets. The title is paragraph zero; content follows it.
- Only composed rows retain TextLayoutResult and actual, unclipped window bounds.
  Rows unregister on disposal. A partially clipped paragraph keeps its real text
  origin, so dragging does not resolve positions against the wrong line.
- Existing Text draws its selected path behind the unchanged glyphs. Selection
  changes redraw highlights/handles without rebuilding annotated chapter text or
  reshaping every paragraph. Gold highlight contrast follows the reader theme.
- The chapter/rewritten-content identity owns the controller. Replaced text clears
  old handles/menus; stale asynchronous Copy cannot affect another chapter.
- Copy/Select All read the chapter model, including paragraphs not currently
  composed. Settings, loading/error cards and the next-chapter footer are excluded.

## Gestures and accessibility

A stable lazy-viewport long-press listener starts word selection and survives row
recycling. The two 48-dp handle targets update the same chapter range. Handle drags
retain their node while their text is clipped/recycled at an edge. Their hit areas
extend to opposite sides to reduce overlap on short words. Accessibility actions
move either endpoint backward/forward by a code point, including across paragraphs.

At a 48-dp top/bottom edge band, one drag-only frame coroutine scrolls the existing
LazyListState, up to 520 dp/second, with capped frame time. It stops away from the
edge, at a boundary, on release/cancel, or when the reader becomes ineligible.
The public platform magnifier is visible only during a drag; Android below API 28
uses Compose's no-op fallback. No idle polling, extra reader or background work
is added.

While a selection exists, chapter pull-navigation and fast-scroll dragging are
inactive. Existing first-touch behavior pauses auto-scroll. A short reading tap
or Back dismisses selection without also opening quick settings. Opening settings,
a drawer, another chapter, or backgrounding clears selection. These gates do not
change normal reader scrolling or explicit chapter controls.

## Private definition menu

ReaderSelectionToolbar uses the supported native floating ActionMode.Callback2,
with a content rectangle computed from visible selected text. Copy runs string
assembly off Main and checks the captured range/lifecycle before writing the
clipboard. Define is visible for a valid single word only, bounded by the existing
DictionaryWord validator (80 code units after normalization; input capture <=512).

Define sends only that word to the same-app DictionaryLookupActivity using an
explicit PROCESS_TEXT Intent. The Activity remains non-exported and has no public
PROCESS_TEXT filter. No external applications gain this menu action. Both readers
reuse existing dictionary definitions, pronunciation, caching and provider backoff.

Dragging or scrolling the selected range entirely offscreen closes the floating
toolbar without clearing the range; release/visible text restores it. Copy and
Select All declare native Ctrl+C/Ctrl+A menu shortcuts. Native toolbar dismissal clears the range, while identity checks prevent an
older toolbar callback from clearing a newer range. Composition/lifecycle disposal
releases native action modes and row-layout registrations.

[ActionMode.Callback2](https://developer.android.com/reference/android/view/ActionMode.Callback2)
and [Compose drag gesture guidance](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-composables)
provide the public APIs used here. No internal Compose selection API is used.

## Verification and remaining checks

Offline debug assembly and scoped diff review are recorded under
build/diagnostics/reader-selection-and-chrome/. No automated tests were added or
run. Device gesture/menu checks remain pending: start/end across many paragraphs,
reverse/edge dragging, partially clipped long paragraphs, Select All/Copy including
offscreen text, source/decoded/rewrite versions, all themes/font sizes, short words,
rotation, settings/drawer, Back/background and dictionary/pronunciation return.
