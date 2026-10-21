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

class FreeWebNovelSource(
    private val client: OkHttpClient = HttpFactory.getClient()
) : NovelSource {

    override val sourceType: NovelSourceType = NovelSourceType.FREE_WEB_NOVEL

    override suspend fun getHomeSections(): Result<List<NovelSection>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(sourceType.baseUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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

            val items = doc.select(".ul-list1 li, .li-row, .index-novel-item, .col-novel")
            items.take(12).forEachIndexed { idx, item ->
                val title = item.selectFirst(".title, h3 a, a[title]")?.text().orEmpty()
                val link = item.selectFirst("a")?.attr("abs:href").orEmpty()
                val img = item.selectFirst("img")?.attr("abs:src")
                    ?: item.selectFirst("img")?.attr("abs:data-src").orEmpty()

                if (title.isNotBlank() && link.isNotBlank()) {
                    parsedNovels.add(
                        Novel(
                            id = "fwn-$idx-${title.hashCode()}",
                            title = title,
                            coverUrl = img.ifBlank { "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600&q=80" },
                            source = sourceType,
                            sourceUrl = link,
                            rating = 4.8f,
                            status = "Ongoing"
                        )
                    )
                }
            }

            if (parsedNovels.isEmpty()) {
                SeedData.getSectionsFor(sourceType)
            } else {
                listOf(
                    NovelSection(
                        id = "fwn-hero",
                        title = "FWN Highlights",
                        iconEmoji = "",
                        subtitle = "Popular free web novels",
                        type = SectionType.HERO_SLIDER,
                        novels = parsedNovels.take(3).ifEmpty { SeedData.fwnHeroSlides }
                    ),
                    NovelSection(
                        id = "fwn-trending",
                        title = "Hot Rankings",
                        iconEmoji = "",
                        subtitle = "Top ranked books on FreeWebNovel",
                        type = SectionType.HORIZONTAL_LIST,
                        novels = parsedNovels.drop(3).ifEmpty { SeedData.fwnTrending }
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

            val searchUrl = "${sourceType.baseUrl}/search?keyword=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@runCatching SeedData.search(sourceType, query)

            val html = response.body?.string().orEmpty()
            val doc = Jsoup.parse(html, sourceType.baseUrl)
            val results = mutableListOf<Novel>()

            doc.select(".ul-list1 li, .li-row").forEachIndexed { idx, item ->
                val title = item.selectFirst(".title, a[title]")?.text().orEmpty()
                val link = item.selectFirst("a")?.attr("abs:href").orEmpty()
                val img = item.selectFirst("img")?.attr("abs:src").orEmpty()

                if (title.isNotBlank()) {
                    results.add(
                        Novel(
                            id = "fwn-s-$idx",
                            title = title,
                            coverUrl = img.ifBlank { "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600&q=80" },
                            source = sourceType,
                            sourceUrl = link,
                            rating = 4.7f
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
            SeedData.fwnHeroSlides.firstOrNull { it.sourceUrl == novelUrl }
                ?: Novel(
                    id = "fwn-det-${novelUrl.hashCode()}",
                    title = "Novel Details",
                    coverUrl = "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600&q=80",
                    source = sourceType,
                    sourceUrl = novelUrl
                )
        }
    }
}
