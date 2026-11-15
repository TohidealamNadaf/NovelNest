package com.novelnest.app.data.local.epub

/** Trusted selection metadata only: no text, paths, navigation commands, or publisher script. */
internal object EpubSelectionDocument {
    val hook = """
        <script type="text/javascript">//<![CDATA[
        (function() {
            if (window.nnSelectionHook === true) return;
            window.nnSelectionHook = true;
            var pending = 0;
            var wasActive = false;
            function active() {
                var selection = window.getSelection && window.getSelection();
                return !!(selection && selection.rangeCount && !selection.isCollapsed);
            }
            function publish() {
                pending = 0;
                var selected = active();
                if (!selected && !wasActive) return;
                wasActive = selected;
                if (window.NovelNestSelectionEvents) {
                    window.NovelNestSelectionEvents.update(selected, window.scrollY || 0, window.innerWidth || 0);
                }
            }
            function schedule() {
                if (!pending) pending = window.requestAnimationFrame(publish);
            }
            document.addEventListener('selectionchange', schedule);
            window.addEventListener('scroll', function() { if (active()) schedule(); }, {passive: true});
            window.addEventListener('resize', function() { if (active()) schedule(); }, {passive: true});
            window.addEventListener('pagehide', function() {
                if (pending) window.cancelAnimationFrame(pending);
                pending = 0;
                if (wasActive && window.NovelNestSelectionEvents) window.NovelNestSelectionEvents.update(false, 0, 0);
                wasActive = false;
            });
        })();
        //]]></script>
    """.trimIndent()
}
