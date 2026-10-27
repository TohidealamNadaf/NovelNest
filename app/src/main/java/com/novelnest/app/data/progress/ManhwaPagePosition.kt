package com.novelnest.app.data.progress

/** Image-reader coordinates only. Prose scroll checkpoints never use or modify these fields. */
data class ManhwaPagePosition(val pageIndex: Int, val offsetFraction: Float, val pageCount: Int) {
    companion object {
        fun validated(pageIndex: Int, offsetFraction: Float, pageCount: Int): ManhwaPagePosition? =
            if (pageCount > 0 && pageIndex in 0 until pageCount && offsetFraction.isFinite())
                ManhwaPagePosition(pageIndex, offsetFraction.coerceIn(0f, 1f), pageCount)
            else null
    }
}
