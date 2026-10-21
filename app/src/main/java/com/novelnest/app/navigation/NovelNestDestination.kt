package com.novelnest.app.navigation

sealed class NovelNestDestination(val route: String) {
    data object Home : NovelNestDestination("home")
    data object Discover : NovelNestDestination("discover")
    data object Search : NovelNestDestination("search/{sourceOrdinal}") {
        fun createRoute(sourceOrdinal: Int) = "search/$sourceOrdinal"
    }
}
