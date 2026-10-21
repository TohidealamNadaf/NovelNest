package com.novelnest.app

import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.repository.NovelRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NovelRepositoryTest {

    private lateinit var repository: NovelRepositoryImpl

    @Before
    fun setup() {
        repository = NovelRepositoryImpl()
    }

    @Test
    fun getHomeSections_emitsSectionsForNovelFire() = runBlocking {
        val result = repository.getHomeSections(NovelSourceType.NOVEL_FIRE).first()
        assertTrue(result.isSuccess)
        val sections = result.getOrNull()
        assertNotNull(sections)
        assertTrue(sections!!.isNotEmpty())
    }

    @Test
    fun getHomeSections_emitsSectionsForAsuraScans() = runBlocking {
        val result = repository.getHomeSections(NovelSourceType.ASURA_SCANS).first()
        assertTrue(result.isSuccess)
        val sections = result.getOrNull()
        assertNotNull(sections)
        assertTrue(sections!!.isNotEmpty())
    }

    @Test
    fun searchNovels_findsMatchingNovelsByTitle() = runBlocking {
        val result = repository.searchNovels(NovelSourceType.NOVEL_FIRE, "Shadow Slave")
        assertTrue(result.isSuccess)
        val novels = result.getOrNull()
        assertNotNull(novels)
        assertTrue(novels!!.any { it.title.contains("Shadow Slave", ignoreCase = true) })
    }

    @Test
    fun searchNovels_inAsuraScans_findsManhwa() = runBlocking {
        val result = repository.searchNovels(NovelSourceType.ASURA_SCANS, "Solo Leveling")
        assertTrue(result.isSuccess)
        val manhwa = result.getOrNull()
        assertNotNull(manhwa)
        assertTrue(manhwa!!.any { it.title.contains("Solo Leveling", ignoreCase = true) })
    }
}
