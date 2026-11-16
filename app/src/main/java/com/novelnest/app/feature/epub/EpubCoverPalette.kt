package com.novelnest.app.feature.epub

import androidx.compose.ui.graphics.Color

internal data class EpubCoverPalette(val top: Color, val middle: Color, val bottom: Color, val accent: Color)

private val coverPalettes = listOf(
    EpubCoverPalette(Color(0xFF28241D), Color(0xFF1C2028), Color(0xFF101318), Color(0xFFE7B86F)),
    EpubCoverPalette(Color(0xFF1C2B3D), Color(0xFF17212E), Color(0xFF0E151F), Color(0xFF9FC5F1)),
    EpubCoverPalette(Color(0xFF203529), Color(0xFF17271E), Color(0xFF101A16), Color(0xFFA9D5AE)),
    EpubCoverPalette(Color(0xFF36263B), Color(0xFF241C2C), Color(0xFF17121D), Color(0xFFD8B1E9)),
    EpubCoverPalette(Color(0xFF163639), Color(0xFF14282B), Color(0xFF0D191C), Color(0xFF9CD8D9)),
    EpubCoverPalette(Color(0xFF3C2828), Color(0xFF2B1D20), Color(0xFF1B1317), Color(0xFFE7AEAE))
)

/** Series IDs keep sibling volumes consistent; standalone books use their immutable content ID. */
internal fun epubCoverPalette(identity: String): EpubCoverPalette =
    coverPalettes[((identity.hashCode().toLong() and 0x7fffffffL) % coverPalettes.size).toInt()]
