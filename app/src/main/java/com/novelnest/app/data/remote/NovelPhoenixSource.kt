package com.novelnest.app.data.remote

import com.novelnest.app.data.model.Novel
import com.novelnest.app.data.model.NovelSection
import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.model.SectionType
import com.novelnest.app.data.remote.network.HttpFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class NovelPhoenixSource(
    private val client: OkHttpClient = HttpFactory.getClient()
) : NovelSource {

    override val sourceType: NovelSourceType = NovelSourceType.NOVEL_PHOENIX

    override suspend fun getHomeSections(): Result<List<NovelSection>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(sourceType.baseUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@runCatching SeedData.getSectionsFor(sourceType)
            }

            val html = response.body?.string().orEmpty()
            if (html.isBlank()) {
                return@runCatching SeedData.getSectionsFor(sourceType)
            }

            val doc = Jsoup.parse(html, sourceType.baseUrl)
            val parsedNovels = mutableListOf<Novel>()

            // NovelPhoenix item selectors
            val items = doc.select("li.novel-item, .novel-item")
            items.take(20).forEachIndexed { index, element ->
                val titleEl = element.selectFirst(".novel-title, h4, a[title]")
                val title = titleEl?.text()?.ifBlank { titleEl.attr("title") } ?: "Novel $index"
                val link = element.selectFirst("a[href*='/novel/'], a")?.attr("abs:href").orEmpty()

                var coverImg = element.selectFirst("img")?.attr("abs:data-src").orEmpty()
                if (coverImg.isBlank() || coverImg.contains("data:image")) {
                    coverImg = element.selectFirst("img")?.attr("abs:src").orEmpty()
                }
                if (coverImg.isBlank()) {
                    coverImg = "https://novelphoenix.com/server-1/shadow-slave.jpg"
                }

                val statsEl = element.selectFirst(".novel-stats, .chapters")
                val latestCh = statsEl?.text().orEmpty()

                val ratingEl = element.selectFirst(".badge, .rating, ._br")
                val rating = ratingEl?.text()?.replace(Regex("[^0-9.]"), "")?.toFloatOrNull() ?: 4.8f

                if (link.isNotBlank() && title.isNotBlank()) {
                    parsedNovels.add(
                        Novel(
                            id = "np-$index-${title.hashCode()}",
                            title = title,
                            coverUrl = coverImg,
                            source = sourceType,
                            sourceUrl = link,
                            latestChapter = latestCh.ifBlank { "Latest Chapters" },
                            rating = rating,
                            status = "Ongoing"
                        )
                    )
                }
            }

            if (parsedNovels.isEmpty()) {
                SeedData.getSectionsFor(sourceType)
            } else {
                val heroList = parsedNovels.take(3)
                val trendingList = parsedNovels.drop(3).take(7)
                val latestList = parsedNovels.drop(10)

                listOf(
                    NovelSection(
                        id = "np-hero",
                        title = "Phoenix Featured",
                        iconEmoji = "🔥",
                        subtitle = "Top picks on NovelPhoenix",
                        type = SectionType.HERO_SLIDER,
                        novels = heroList.ifEmpty { SeedData.novelPhoenixHeroSlides }
                    ),
                    NovelSection(
                        id = "np-trending",
                        title = "Top Trending Novels",
                        iconEmoji = "⚡",
                        subtitle = "Most read on NovelPhoenix this week",
                        type = SectionType.HORIZONTAL_LIST,
                        novels = trendingList.ifEmpty { SeedData.novelPhoenixTrending }
                    ),
                    NovelSection(
                        id = "np-latest",
                        title = "Recently Added & Completed",
                        iconEmoji = "✨",
                        subtitle = "Fresh novel updates",
                        type = SectionType.HORIZONTAL_LIST,
                        novels = latestList.ifEmpty { SeedData.novelPhoenixLatest }
                    )
                )
            }
        }.recover {
            SeedData.getSectionsFor(sourceType)
        }
    }

    override suspend fun search(query: String): Result<List<Novel>> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank()) {
                return@runCatching SeedData.search(sourceType, "")
            }

            val searchUrl = "${sourceType.baseUrl}/search?keyword=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder()
                .url(searchUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@runCatching SeedData.search(sourceType, query)
            }

            val html = response.body?.string().orEmpty()
            val doc = Jsoup.parse(html, sourceType.baseUrl)
            val results = mutableListOf<Novel>()

            val items = doc.select("li.novel-item, .novel-item")
            items.forEachIndexed { idx, item ->
                val titleEl = item.selectFirst(".novel-title, h4, a[title]")
                val title = titleEl?.text()?.ifBlank { titleEl.attr("title") }.orEmpty()
                val link = item.selectFirst("a[href*='/novel/'], a")?.attr("abs:href").orEmpty()

                var img = item.selectFirst("img")?.attr("abs:data-src").orEmpty()
                if (img.isBlank() || img.contains("data:image")) {
                    img = item.selectFirst("img")?.attr("abs:src").orEmpty()
                }

                val statsEl = item.selectFirst(".novel-stats")
                val chapters = statsEl?.text().orEmpty()

                val ratingEl = item.selectFirst(".badge, ._br")
                val rating = ratingEl?.text()?.replace(Regex("[^0-9.]"), "")?.toFloatOrNull() ?: 4.7f

                if (title.isNotBlank() && link.isNotBlank()) {
                    results.add(
                        Novel(
                            id = "np-search-$idx",
                            title = title,
                            coverUrl = img.ifBlank { "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&q=80" },
                            source = sourceType,
                            sourceUrl = link,
                            latestChapter = chapters.ifBlank { "Chapter 1" },
                            rating = rating,
                            status = "Ongoing"
                        )
                    )
                }
            }

            if (results.isEmpty()) {
                SeedData.search(sourceType, query)
            } else {
                results
            }
        }.recover {
            SeedData.search(sourceType, query)
        }
    }

    override suspend fun getNovelDetails(novelUrl: String): Result<Novel> = withContext(Dispatchers.IO) {
        runCatching {
            val novel = SeedData.novelPhoenixHeroSlides.firstOrNull { it.sourceUrl == novelUrl }
                ?: Novel(
                    id = "np-details-${novelUrl.hashCode()}",
                    title = "Novel Details",
                    coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&q=80",
                    source = sourceType,
                    sourceUrl = novelUrl
                )
            novel
        }
    }
}
