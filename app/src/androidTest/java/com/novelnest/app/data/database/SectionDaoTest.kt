package com.novelnest.app.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.data.database.dao.NovelDao
import com.novelnest.app.data.database.dao.SectionDao
import com.novelnest.app.data.database.entity.NovelEntity
import com.novelnest.app.data.database.entity.SectionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SectionDaoTest {

    private lateinit var database: NovelNestDatabase
    private lateinit var sectionDao: SectionDao
    private lateinit var novelDao: NovelDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NovelNestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sectionDao = database.sectionDao()
        novelDao = database.novelDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createTestSection(
        sectionId: String,
        source: String = "NOVEL_PHOENIX",
        title: String = "Trending Now",
        displayOrder: Int = 0
    ) = SectionEntity(
        sectionId = sectionId,
        source = source,
        title = title,
        iconEmoji = "🔥",
        subtitle = "Top reads today",
        displayOrder = displayOrder
    )

    private fun createTestNovel(id: String, title: String) = NovelEntity(
        id = id,
        title = title,
        coverUrl = "https://example.com/cover.jpg",
        source = "NOVEL_PHOENIX",
        sourceUrl = "https://example.com/novel/$id"
    )

    @Test
    fun insertSections_orderedByDisplayOrder() = runBlocking {
        val s1 = createTestSection(sectionId = "sec-3", displayOrder = 3, title = "Latest")
        val s2 = createTestSection(sectionId = "sec-1", displayOrder = 1, title = "Hero Carousel")
        val s3 = createTestSection(sectionId = "sec-2", displayOrder = 2, title = "Popular")

        sectionDao.insertSections(listOf(s1, s2, s3))

        val retrieved = sectionDao.getSectionsForSource("NOVEL_PHOENIX").first()
        assertEquals(3, retrieved.size)
        assertEquals("sec-1", retrieved[0].sectionId)
        assertEquals("sec-2", retrieved[1].sectionId)
        assertEquals("sec-3", retrieved[2].sectionId)
    }

    @Test
    fun saveSectionWithNovels_preservesExactNovelOrdering() = runBlocking {
        val section = createTestSection(sectionId = "trending-1")
        val novelA = createTestNovel("nov-A", "First Book")
        val novelB = createTestNovel("nov-B", "Second Book")
        val novelC = createTestNovel("nov-C", "Third Book")

        // Insert novels into novels table first
        novelDao.upsertNovels(listOf(novelA, novelB, novelC))

        // Save section with ordered novels [novelB, novelA, novelC]
        sectionDao.saveSectionWithNovels(section, listOf(novelB, novelA, novelC))

        val sectionNovels = sectionDao.getNovelsForSection("trending-1").first()
        assertEquals(3, sectionNovels.size)
        assertEquals("nov-B", sectionNovels[0].id)
        assertEquals("nov-A", sectionNovels[1].id)
        assertEquals("nov-C", sectionNovels[2].id)
    }

    @Test
    fun saveSectionWithNovels_replaceBehavior_updatesRelationships() = runBlocking {
        val section = createTestSection(sectionId = "replaceable-sec")
        val n1 = createTestNovel("n-1", "Book 1")
        val n2 = createTestNovel("n-2", "Book 2")
        val n3 = createTestNovel("n-3", "Book 3")

        novelDao.upsertNovels(listOf(n1, n2, n3))

        // Initial assignment: [n1, n2]
        sectionDao.saveSectionWithNovels(section, listOf(n1, n2))
        var novels = sectionDao.getNovelsForSection("replaceable-sec").first()
        assertEquals(2, novels.size)
        assertEquals("n-1", novels[0].id)
        assertEquals("n-2", novels[1].id)

        // Replacement assignment: [n3, n1]
        sectionDao.saveSectionWithNovels(section, listOf(n3, n1))
        novels = sectionDao.getNovelsForSection("replaceable-sec").first()
        assertEquals(2, novels.size)
        assertEquals("n-3", novels[0].id)
        assertEquals("n-1", novels[1].id)
    }

    @Test
    fun queryEmptySection_returnsEmptyList() = runBlocking {
        val emptySection = createTestSection(sectionId = "empty-sec")
        sectionDao.insertSections(listOf(emptySection))

        val novels = sectionDao.getNovelsForSection("empty-sec").first()
        assertTrue(novels.isEmpty())
    }

    @Test
    fun clearCrossRefsForSection_removesAssociationsWhileNovelsRemain() = runBlocking {
        val section = createTestSection(sectionId = "clear-sec")
        val novel = createTestNovel("n-clear", "Persistent Novel")

        novelDao.upsertNovel(novel)
        sectionDao.saveSectionWithNovels(section, listOf(novel))

        // Verify association exists
        assertEquals(1, sectionDao.getNovelsForSection("clear-sec").first().size)

        // Clear cross references
        sectionDao.clearCrossRefsForSection("clear-sec")
        assertTrue(sectionDao.getNovelsForSection("clear-sec").first().isEmpty())

        // Novel itself must still exist in novels table
        val novelInDb = novelDao.getNovelById("n-clear").first()
        assertNotNull(novelInDb)
        assertEquals("n-clear", novelInDb?.id)
    }
}
