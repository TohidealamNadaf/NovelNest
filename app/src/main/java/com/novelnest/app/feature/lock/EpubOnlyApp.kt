package com.novelnest.app.feature.lock

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.feature.epub.EpubLibraryScreen
import com.novelnest.app.feature.epub.EpubLibraryViewModel
import com.novelnest.app.feature.epub.EpubReaderActivity

/** An independent allow-list graph: full-app destinations are never registered or restored here. */
@Composable
fun EpubOnlyApp() {
    val context = LocalContext.current
    val app = context.applicationContext as NovelNestApplication
    val nav = rememberNavController()
    NavHost(nav, startDestination = "epub_library") {
        composable("epub_library") {
            val model: EpubLibraryViewModel = viewModel(factory = shelfFactory(app, null))
            EpubLibraryScreen(model, onBack = {},
                onOpenBook = { EpubReaderActivity.open(context, it) },
                onOpenSeries = { nav.navigate("epub_series/" + android.net.Uri.encode(it)) },
                restrictedRoot = true, onSettings = { nav.navigate("epub_preferences") { launchSingleTop = true } })
        }
        composable("epub_series/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val model: EpubLibraryViewModel = viewModel(factory = shelfFactory(app, entry.arguments?.getString("id")))
            EpubLibraryScreen(model, onBack = { nav.popBackStack() },
                onOpenBook = { EpubReaderActivity.open(context, it) },
                onOpenSeries = { nav.navigate("epub_series/" + android.net.Uri.encode(it)) })
        }
        composable("epub_preferences") {
            EpubOnlySettings(app, onBack = { nav.popBackStack() })
        }
    }
}
private fun shelfFactory(app: NovelNestApplication, collection: String?) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        EpubLibraryViewModel(app.epubRepository, collection, extras.createSavedStateHandle()) as T
}