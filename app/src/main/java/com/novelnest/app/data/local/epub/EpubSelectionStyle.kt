package com.novelnest.app.data.local.epub

import org.readium.r2.navigator.epub.css.Color
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.shared.ExperimentalReadiumApi

/** Opaque selection colors keep the same contrast regardless of the reader or publisher theme. */
@OptIn(ExperimentalReadiumApi::class)
internal object EpubSelectionStyle {
    private const val BACKGROUND = "#E2AA62"
    private const val TEXT = "#171A1F"

    val readiumProperties = RsProperties(
        selectionBackgroundColor = Color.Hex(BACKGROUND),
        selectionTextColor = Color.Hex(TEXT)
    )

    // Also cover publisher selection rules, nested spans/links and WebKit text-fill overrides.
    // These are paint-only pseudo-element rules: they never change layout or selection ranges.
    val css = """
        :root::selection,
        :root *::selection {
            background-color: $BACKGROUND !important;
            color: $TEXT !important;
            -webkit-text-fill-color: $TEXT !important;
            text-shadow: none !important;
        }
    """.trimIndent()
}
