package com.novelnest.app.navigation

sealed class NovelNestDestination(val route: String) {
    data object Home : NovelNestDestination("home")
    data object Discover : NovelNestDestination("discover")
    data object Settings : NovelNestDestination("settings")
    data object Search : NovelNestDestination("search/{sourceOrdinal}") {
        fun createRoute(sourceOrdinal: Int) = "search/$sourceOrdinal"
    }
    data object Reader : NovelNestDestination("reader/{novelId}?chapterIndex={chapterIndex}") {
        fun createRoute(novelId: String, chapterIndex: Int = -1) = "reader/$novelId?chapterIndex=$chapterIndex"
    }
    data object Detail : NovelNestDestination("detail/{novelId}") {
        fun createRoute(novelId: String) = "detail/$novelId"
    }
    data object Notifications : NovelNestDestination("notifications")
}
