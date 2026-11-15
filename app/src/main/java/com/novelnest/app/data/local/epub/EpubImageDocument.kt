package com.novelnest.app.data.local.epub

/** Added after publication sanitization. The bridge accepts an index, never a file path or script. */
internal object EpubImageDocument {
    private val hook = """
        <script type="text/javascript">//<![CDATA[
        (function() {
            if (window.nnImageHook === true) return;
            window.nnImageHook = true;
            document.addEventListener('click', function(event) {
                if (event.defaultPrevented || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button > 0) return;
                if (window.getSelection && !window.getSelection().isCollapsed) return;
                var target = event.target.closest && event.target.closest('img, svg');
                if (!target || !window.NovelNestImageTap) return;
                var visuals = Array.from(document.body.querySelectorAll('img, svg')).filter(function(el) { return !el.parentElement.closest('svg'); });
                while (target.parentElement && target.parentElement.closest('svg')) target = target.parentElement.closest('svg');
                var index = visuals.indexOf(target);
                if (index < 0 || index >= 256) return;
                event.preventDefault(); event.stopImmediatePropagation();
                window.NovelNestImageTap.open(index);
            }, true);
        })();
        //]]></script>
    """.trimIndent()

    fun attach(bytes: ByteArray): ByteArray {
        val html = bytes.toString(Charsets.UTF_8)
        val end = html.lastIndexOf("</body>")
        return if (end < 0) bytes else (html.substring(0, end) + hook + EpubSelectionDocument.hook + html.substring(end)).toByteArray(Charsets.UTF_8)
    }
}
