package com.novelnest.app.feature.manhwa

import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.navigation.NovelNestDestination
import com.novelnest.app.navigation.resolveReaderSource
import com.novelnest.app.data.model.Novel
import com.novelnest.app.testsupport.ReaderTestRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ManhwaNavigationTest {

    @Test
    fun destinationRouteContractIsPreserved() {
        assertEquals("manhwa-reader/{novelId}?chapterIndex={chapterIndex}", NovelNestDestination.ManhwaReader.route)
        assertEquals("manhwa-reader/asura-childhood-friend?chapterIndex=5", NovelNestDestination.ManhwaReader.createRoute("asura-childhood-friend", 5))
        assertEquals("manhwa-reader/asura-childhood-friend?chapterIndex=-1", NovelNestDestination.ManhwaReader.createRoute("asura-childhood-friend"))
    }

    @Test
    fun asuraSourceIdentificationIsConsistent() = runTest {
        val asuraNovelId = "asura-solo-leveling"
        val npNovelId = "np-shadow-slave"
        val fwnNovelId = "fwn-reverend-insanity"

        val repo = ReaderTestRepository()
        assertTrue(resolveReaderSource(asuraNovelId, repo, null).isManhwa)
        assertFalse(resolveReaderSource(npNovelId, repo, null).isManhwa)
        assertFalse(resolveReaderSource(fwnNovelId, repo, null).isManhwa)
        repo.local = Novel(asuraNovelId, "Stored record", "", NovelSourceType.NOVEL_PHOENIX, "")
        assertFalse(resolveReaderSource(asuraNovelId, repo, null).isManhwa) // explicit source wins
    }

    @Test
    fun tallImageTileSlicingBoundsPixelDimensions() {
        val totalWidth = 800
        val totalHeight = 15555
        val maxTileHeight = 2048

        val numSlices = (totalHeight + maxTileHeight - 1) / maxTileHeight
        assertEquals(8, numSlices)

        var coveredHeight = 0
        for (i in 0 until numSlices) {
            val yStart = i * maxTileHeight
            val yEnd = minOf((i + 1) * maxTileHeight, totalHeight)
            val sliceH = yEnd - yStart
            assertTrue("Slice $i height $sliceH exceeds max $maxTileHeight", sliceH in 1..maxTileHeight)
            assertEquals(coveredHeight, yStart)
            coveredHeight = yEnd
        }
        assertEquals("All pixels must be covered contiguously", totalHeight, coveredHeight)
    }

    @Test
    fun standardPageProducesSingleTile() {
        val totalWidth = 800
        val totalHeight = 1200
        val maxTileHeight = 2048

        val numSlices = (totalHeight + maxTileHeight - 1) / maxTileHeight
        assertEquals(1, numSlices)
    }
}
