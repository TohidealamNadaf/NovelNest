package com.novelnest.app.data.remote.freewebnovel

import com.novelnest.app.data.remote.SourceRouteContract
import com.novelnest.app.data.model.NovelSourceType

class FreeWebNovelRouteTest : SourceRouteContract() {
    override val kind = NovelSourceType.FREE_WEB_NOVEL
}
