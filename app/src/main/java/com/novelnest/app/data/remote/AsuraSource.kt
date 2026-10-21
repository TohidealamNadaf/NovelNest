package com.novelnest.app.data.remote

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.model.SectionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class AsuraSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : NovelSource {

    override val sourceType: NovelSourceType = NovelSourceType.ASURA_SCANS

    override suspend fun getHomeSections(): Result<List<NovelSection>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(sourceType.baseUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@runCatching SeedData.getSectionsFor(sourceType)

            val html = response.body?.string().orEmpty()
            if (html.isBlank()) return@runCatching SeedData.getSectionsFor(sourceType)

            val doc = Jsoup.parse(html, sourceType.baseUrl)
            val parsedManhwa = mutableListOf<Novel>()

            val items = doc.select(".grid .col-span-1, .listupd .bsx, .utao .uta")
            items.take(15).forEachIndexed { idx, item ->
                val title = item.selectFirst(".title, .tt, h4, a[title]")?.text().orEmpty()
                val link = item.selectFirst("a")?.attr("abs:href").orEmpty()
                val img = item.selectFirst("img")?.attr("abs:src")
                    ?: item.selectFirst("img")?.attr("abs:data-src").orEmpty()
                val ch = item.selectFirst(".epxs, .chapter, .ch")?.text().orEmpty()

                if (title.isNotBlank()) {
                    parsedManhwa.add(
                        Novel(
                            id = "asura-$idx-${title.hashCode()}",
                            title = title,
                            coverUrl = img.ifBlank { "https://images.unsplash.com/photo-1563089145-599997674d42?w=600&q=80" },
                            source = sourceType,
                            sourceUrl = link,
                            latestChapter = ch.ifBlank { "Chapter 1" },
                            rating = 4.9f,
                            genres = listOf("Manhwa", "Action", "Fantasy")
                        )
                    )
                }
            }

            if (parsedManhwa.isEmpty()) {
                SeedData.getSectionsFor(sourceType)
            } else {
                listOf(
                    NovelSection(
                        id = "asura-hero",
                        title = "Top Manhwa Spotlight",
                        iconEmoji = "⚔️",
                        subtitle = "High adrenaline Korean manhwa",
                        type = SectionType.HERO_SLIDER,
                        novels = parsedManhwa.take(3).ifEmpty { SeedData.asuraHeroSlides }
                    ),
                    NovelSection(
                        id = "asura-trending",
                        title = "Most Popular Manhwa",
                        iconEmoji = "🔥",
                        subtitle = "Trending updates from Asura Scans",
                        type = SectionType.HORIZONTAL_LIST,
                        novels = parsedManhwa.drop(3).take(6).ifEmpty { SeedData.asuraTrending }
                    ),
                    NovelSection(
                        id = "asura-latest",
                        title = "Fresh Releases",
                        iconEmoji = "⚡",
                        subtitle = "Brand new manhwa chapters",
                        type = SectionType.HORIZONTAL_LIST,
                        novels = parsedManhwa.drop(9).ifEmpty { SeedData.asuraLatest }
                    )
                )
            }
        }.recover {
            SeedData.getSectionsFor(sourceType)
        }
    }

    override suspend fun search(query: String): Result<List<Novel>> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank()) return@runCatching SeedData.search(sourceType, "")

            val searchUrl = "${sourceType.baseUrl}/?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@runCatching SeedData.search(sourceType, query)

            val html = response.body?.string().orEmpty()
            val doc = Jsoup.parse(html, sourceType.baseUrl)
            val results = mutableListOf<Novel>()

            doc.select(".bsx, .listupd .bs").forEachIndexed { idx, item ->
                val title = item.selectFirst(".tt, h4, a[title]")?.text().orEmpty()
                val link = item.selectFirst("a")?.attr("abs:href").orEmpty()
                val img = item.selectFirst("img")?.attr("abs:src").orEmpty()

                if (title.isNotBlank()) {
                    results.add(
                        Novel(
                            id = "asura-s-$idx",
                            title = title,
                            coverUrl = img.ifBlank { "https://images.unsplash.com/photo-1563089145-599997674d42?w=600&q=80" },
                            source = sourceType,
                            sourceUrl = link,
                            rating = 4.85f,
                            genres = listOf("Manhwa", "Action")
                        )
                    )
                }
            }

            if (results.isEmpty()) SeedData.search(sourceType, query) else results
        }.recover {
            SeedData.search(sourceType, query)
        }
    }

    override suspend fun getNovelDetails(novelUrl: String): Result<Novel> = withContext(Dispatchers.IO) {
        runCatching {
            SeedData.asuraHeroSlides.firstOrNull { it.sourceUrl == novelUrl }
                ?: Novel(
                    id = "asura-det-${novelUrl.hashCode()}",
                    title = "Manhwa Details",
                    coverUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=600&q=80",
                    source = sourceType,
                    sourceUrl = novelUrl
                )
        }
    }
}
