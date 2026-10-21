package com.novelnest.app.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelnest.app.data.database.dao.NovelDao
import com.novelnest.app.data.database.entity.NovelEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NovelDaoTest {

    private lateinit var database: NovelNestDatabase
    private lateinit var novelDao: NovelDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NovelNestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        novelDao = database.novelDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createTestNovel(
        id: String = "novel-1",
        title: String = "Shadow Monarch",
        source: String = "NOVEL_PHOENIX",
        synopsis: String = "Hunter wakes up with necromancy powers."
    ) = NovelEntity(
        id = id,
        title = title,
        coverUrl = "https://example.com/cover.jpg",
        source = source,
        sourceUrl = "https://example.com/novel/$id",
        synopsis = synopsis,
        author = "Chugong",
        rating = 4.9f,
        latestChapter = "Chapter 200",
        genres = listOf("Action", "Fantasy"),
        status = "Completed",
        views = "1.5M",
        rank = 1
    )

    @Test
    fun insertAndQueryById_returnsMatchingNovel() = runBlocking {
        val novel = createTestNovel(id = "n-100", title = "Omniscient Reader")
        novelDao.upsertNovel(novel)

        val retrieved = novelDao.getNovelById("n-100").first()
        assertNotNull(retrieved)
        assertEquals("n-100", retrieved?.id)
        assertEquals("Omniscient Reader", retrieved?.title)
        assertEquals("NOVEL_PHOENIX", retrieved?.source)
        assertEquals("Chugong", retrieved?.author)
    }

    @Test
    fun upsertUpdatesExistingNovelFields() = runBlocking {
        val initial = createTestNovel(id = "n-101", title = "Initial Title")
        novelDao.upsertNovel(initial)

        val updated = initial.copy(
            title = "Updated Title",
            latestChapter = "Chapter 250",
            rating = 5.0f
        )
        novelDao.upsertNovel(updated)

        val retrieved = novelDao.getNovelById("n-101").first()
        assertNotNull(retrieved)
        assertEquals("Updated Title", retrieved?.title)
        assertEquals("Chapter 250", retrieved?.latestChapter)
        assertEquals(5.0f, retrieved?.rating)
    }

    @Test
    fun upsertMultipleNovelsAndQueryBySource() = runBlocking {
        val novels = listOf(
            createTestNovel(id = "np-1", source = "NOVEL_PHOENIX"),
            createTestNovel(id = "np-2", source = "NOVEL_PHOENIX"),
            createTestNovel(id = "fwn-1", source = "FREE_WEB_NOVEL")
        )
        novelDao.upsertNovels(novels)

        val phoenixNovels = novelDao.getNovelsBySource("NOVEL_PHOENIX").first()
        assertEquals(2, phoenixNovels.size)
        assertTrue(phoenixNovels.any { it.id == "np-1" })
        assertTrue(phoenixNovels.any { it.id == "np-2" })

        val fwnNovels = novelDao.getNovelsBySource("FREE_WEB_NOVEL").first()
        assertEquals(1, fwnNovels.size)
        assertEquals("fwn-1", fwnNovels.first().id)
    }

    @Test
    fun searchNovels_matchesTitleAndSynopsis() = runBlocking {
        val n1 = createTestNovel(id = "s-1", title = "Lord of the Mysteries", synopsis = "Fool starts a tarot club")
        val n2 = createTestNovel(id = "s-2", title = "Reverend Insanity", synopsis = "Gu master rebirth")
        novelDao.upsertNovels(listOf(n1, n2))

        val titleMatch = novelDao.searchNovels("NOVEL_PHOENIX", "Mysteries").first()
        assertEquals(1, titleMatch.size)
        assertEquals("s-1", titleMatch.first().id)

        val synopsisMatch = novelDao.searchNovels("NOVEL_PHOENIX", "rebirth").first()
        assertEquals(1, synopsisMatch.size)
        assertEquals("s-2", synopsisMatch.first().id)
    }

    @Test
    fun queryNonExistentNovel_returnsNull() = runBlocking {
        val result = novelDao.getNovelById("does-not-exist").first()
        assertNull(result)
    }
}
