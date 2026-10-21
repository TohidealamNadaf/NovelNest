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

    val novelRepository: NovelRepository by lazy {
        NovelRepositoryImpl(database = database)
    }

    override fun onCreate() {
        super.onCreate()
        HttpFactory.initialize(cacheDir)
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
