package com.novelnest.app.navigation

sealed class NovelNestDestination(val route: String) {
    data object Home : NovelNestDestination("home")
    data object Discover : NovelNestDestination("discover")
    data object Settings : NovelNestDestination("settings")
    data object Search : NovelNestDestination("search/{sourceOrdinal}") {
        fun createRoute(sourceOrdinal: Int) = "search/$sourceOrdinal"
    }
    data object Reader : NovelNestDestination("reader/{novelId}") {
        fun createRoute(novelId: String) = "reader/$novelId"
    }
    data object Detail : NovelNestDestination("detail/{novelId}") {
        fun createRoute(novelId: String) = "detail/$novelId"
    }
    data object Notifications : NovelNestDestination("notifications")
}
