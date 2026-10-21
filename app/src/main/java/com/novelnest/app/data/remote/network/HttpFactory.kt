package com.novelnest.app.data.remote.network

import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object HttpFactory {

    @Volatile
    private var sharedClient: OkHttpClient? = null

    fun initialize(cacheDir: File): OkHttpClient {
        val client = OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http_cache"), 50L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                    .build()
                chain.proceed(request)
            }
            .build()
        sharedClient = client
        return client
    }

    fun getClient(): OkHttpClient {
        return sharedClient ?: OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
