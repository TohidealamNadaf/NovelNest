package com.novelnest.app.data.remote.novelphoenix

import com.novelnest.app.data.remote.SourceRouteContract
import com.novelnest.app.data.model.NovelSourceType

class NovelPhoenixRouteTest : SourceRouteContract() {
    override val kind = NovelSourceType.NOVEL_PHOENIX
}
