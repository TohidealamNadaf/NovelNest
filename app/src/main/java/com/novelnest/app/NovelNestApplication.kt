package com.novelnest.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.novelnest.app.data.database.NovelNestDatabase
import com.novelnest.app.data.remote.network.HttpFactory
import com.novelnest.app.data.repository.NovelRepository
import com.novelnest.app.data.repository.NovelRepositoryImpl

class NovelNestApplication : Application(), ImageLoaderFactory {

    val database: NovelNestDatabase by lazy {
        NovelNestDatabase.getInstance(this)
    }

    val readerCacheStore by lazy {
        com.novelnest.app.data.cache.ReaderCacheStore(java.io.File(cacheDir, "reader_resume"))
    }

    val novelRepository: NovelRepository by lazy {
        NovelRepositoryImpl(database = database, readerCache = readerCacheStore)
    }

    val preferencesManager: com.novelnest.app.data.preferences.ReaderPreferencesManager by lazy {
        com.novelnest.app.data.preferences.ReaderPreferencesManager(this)
    }

    val bookmarkManager: com.novelnest.app.data.bookmark.BookmarkManager by lazy {
        com.novelnest.app.data.bookmark.BookmarkManager(this, onBookmarked = {
            notificationCoordinator.bookmarkAdded(it)
        })
    }

    val searchHistoryManager: com.novelnest.app.data.preferences.SearchHistoryManager by lazy {
        com.novelnest.app.data.preferences.SearchHistoryManager(this)
    }

    val navigationPreferences by lazy {
        com.novelnest.app.data.preferences.NavigationPreferences(
            // Reuse the existing preference file for a one-time legacy-key cleanup.
            getSharedPreferences("reader_session", MODE_PRIVATE),
            com.novelnest.app.data.model.NovelSourceType.fromOrdinal(preferencesManager.appSettings.value.defaultSourceOrdinal)
        )
    }

    val profileManager by lazy {
        com.novelnest.app.data.profile.ProfileManager(getSharedPreferences("profile", MODE_PRIVATE))
    }
    val notificationStore by lazy {
        com.novelnest.app.data.notification.NotificationStore(getSharedPreferences("notifications", MODE_PRIVATE))
    }
    val notificationCoordinator by lazy { com.novelnest.app.data.notification.NotificationCoordinator(this) }

    val readingProgressManager: com.novelnest.app.data.progress.ReadingProgressManager by lazy {
        com.novelnest.app.data.progress.ReadingProgressManager(database.readingProgressDao(),
            onChapterRead = profileManager::recordReading, onHistoryLoaded = profileManager::recordReadings)
    }

    override fun onCreate() {
        super.onCreate()
        HttpFactory.initialize(cacheDir)
        notificationCoordinator.initialize()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB image cache
                    .build()
            }
            .okHttpClient { HttpFactory.getClient() }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}
