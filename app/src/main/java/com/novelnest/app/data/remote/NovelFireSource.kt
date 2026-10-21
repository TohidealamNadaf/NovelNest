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

class NovelFireSource(
    private val client: OkHttpClient = HttpFactory.getClient()
) : NovelSource {

    override val sourceType: NovelSourceType = NovelSourceType.NOVEL_FIRE

    override suspend fun getHomeSections(): Result<List<NovelSection>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(sourceType.baseUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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

            // NovelFire card selectors
            val items = doc.select(".novel-item, .list-item, .post-item, .item-thumb")
            items.take(15).forEachIndexed { index, element ->
                val titleEl = element.selectFirst(".novel-title, .title, a[title]")
                val title = titleEl?.text()?.ifBlank { titleEl.attr("title") } ?: "Novel $index"
                val link = element.selectFirst("a")?.attr("abs:href").orEmpty()
                val coverImg = element.selectFirst("img")?.attr("abs:src")
                    ?: element.selectFirst("img")?.attr("abs:data-src").orEmpty()
                val latestCh = element.selectFirst(".chapter-title, .chapter, .ch-item")?.text().orEmpty()

                if (link.isNotBlank() && title.isNotBlank()) {
                    parsedNovels.add(
                        Novel(
                            id = "nf-$index-${title.hashCode()}",
                            title = title,
                            coverUrl = coverImg.ifBlank { "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&q=80" },
                            source = sourceType,
                            sourceUrl = link,
                            latestChapter = latestCh.ifBlank { "Latest" },
                            rating = 4.8f,
                            status = "Ongoing"
                        )
                    )
                }
            }

            if (parsedNovels.isEmpty()) {
                SeedData.getSectionsFor(sourceType)
            } else {
                val heroList = parsedNovels.take(3)
                val trendingList = parsedNovels.drop(3).take(6)
                val latestList = parsedNovels.drop(9)

                listOf(
                    NovelSection(
                        id = "nf-hero",
                        title = "Featured Masterpieces",
                        iconEmoji = "✨",
                        subtitle = "Handpicked epic web novels",
                        type = SectionType.HERO_SLIDER,
                        novels = heroList.ifEmpty { SeedData.novelFireHeroSlides }
                    ),
                    NovelSection(
                        id = "nf-trending",
                        title = "Top Trending Novels",
                        iconEmoji = "🔥",
                        subtitle = "Most read on NovelFire",
                        type = SectionType.HORIZONTAL_LIST,
                        novels = trendingList.ifEmpty { SeedData.novelFireTrending }
                    ),
                    NovelSection(
                        id = "nf-latest",
                        title = "Latest Chapter Releases",
                        iconEmoji = "⚡",
                        subtitle = "Freshly translated chapters",
                        type = SectionType.HORIZONTAL_LIST,
                        novels = latestList.ifEmpty { SeedData.novelFireLatest }
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
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@runCatching SeedData.search(sourceType, query)
            }

            val html = response.body?.string().orEmpty()
            val doc = Jsoup.parse(html, sourceType.baseUrl)
            val results = mutableListOf<Novel>()

            val items = doc.select(".novel-item, .search-item, .list-item")
            items.forEachIndexed { idx, item ->
                val titleEl = item.selectFirst(".title, a[title]")
                val title = titleEl?.text()?.ifBlank { titleEl.attr("title") }.orEmpty()
                val link = item.selectFirst("a")?.attr("abs:href").orEmpty()
                val img = item.selectFirst("img")?.attr("abs:src")
                    ?: item.selectFirst("img")?.attr("abs:data-src").orEmpty()

                if (title.isNotBlank() && link.isNotBlank()) {
                    results.add(
                        Novel(
                            id = "nf-search-$idx",
                            title = title,
                            coverUrl = img.ifBlank { "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&q=80" },
                            source = sourceType,
                            sourceUrl = link,
                            rating = 4.7f,
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
            val novel = SeedData.novelFireHeroSlides.firstOrNull { it.sourceUrl == novelUrl }
                ?: Novel(
                    id = "nf-details-${novelUrl.hashCode()}",
                    title = "Novel Details",
                    coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&q=80",
                    source = sourceType,
                    sourceUrl = novelUrl
                )
            novel
        }
    }
}
