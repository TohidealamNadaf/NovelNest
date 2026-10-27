package com.novelnest.app.feature.manhwa

import com.novelnest.app.data.progress.ManhwaPagePosition

internal fun manhwaPagePosition(firstIndex: Int, offset: Int, itemSize: Int, pageCount: Int): ManhwaPagePosition? {
    if (pageCount <= 0 || firstIndex < 0) return null
    // The footer is a separate lazy-list item, not an additional chapter image.
    if (firstIndex >= pageCount) return ManhwaPagePosition(pageCount - 1, 1f, pageCount)
    if (itemSize <= 0) return null
    return ManhwaPagePosition.validated(firstIndex, offset.toFloat() / itemSize, pageCount)
}

internal fun manhwaChapterEndReached(pageCount: Int, firstIndex: Int, lastPageBottom: Int?, viewportEnd: Int, footerVisible: Boolean): Boolean =
    pageCount > 0 && (firstIndex >= pageCount || footerVisible || (lastPageBottom != null && lastPageBottom <= viewportEnd))
