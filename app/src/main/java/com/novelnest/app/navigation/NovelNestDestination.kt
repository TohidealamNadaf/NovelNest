package com.novelnest.app.navigation

sealed class NovelNestDestination(val route: String) {
    data object Home : NovelNestDestination("home")
    data object Discover : NovelNestDestination("discover")
    data object Browse : NovelNestDestination("browse/{sourceOrdinal}") {
        fun createRoute(sourceOrdinal: Int) = "browse/$sourceOrdinal"
    }
    data object Profile : NovelNestDestination("profile")
    data object Settings : NovelNestDestination("settings")
    data object Search : NovelNestDestination("search/{sourceOrdinal}") {
        fun createRoute(sourceOrdinal: Int) = "search/$sourceOrdinal"
    }
    data object Reader : NovelNestDestination("reader/{novelId}?chapterIndex={chapterIndex}") {
        fun createRoute(novelId: String, chapterIndex: Int = -1) = "reader/$novelId?chapterIndex=$chapterIndex"
    }
    /** Registered by the dedicated image-reader UI; never send image chapters to ReaderScreen. */
    data object ManhwaReader : NovelNestDestination("manhwa-reader/{novelId}?chapterIndex={chapterIndex}") {
        fun createRoute(novelId: String, chapterIndex: Int = -1) = "manhwa-reader/$novelId?chapterIndex=$chapterIndex"
    }
    data object Detail : NovelNestDestination("detail/{novelId}") {
        fun createRoute(novelId: String) = "detail/$novelId"
    }
    data object Notifications : NovelNestDestination("notifications")
}
