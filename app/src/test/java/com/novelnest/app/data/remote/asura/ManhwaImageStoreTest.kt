package com.novelnest.app.data.remote.asura

import com.novelnest.app.data.manhwa.ManhwaImageStore
import com.novelnest.app.data.model.ManhwaPage
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ManhwaImageStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val image = "RIFF1234WEBPdata".toByteArray()
    private fun page(index: Int) = ManhwaPage(index, "https://cdn.asurascans.com/asura-images/chapters/test/1/$index.webp", 800, 15555)
    private fun client(count: AtomicInteger, response: (Request) -> ByteArray = { image }) = OkHttpClient.Builder().addInterceptor { chain ->
        count.incrementAndGet()
        assertEquals(ASURA_CHAPTER, chain.request().header("Referer"))
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
            .body(response(chain.request()).toResponseBody("image/webp".toMediaType())).build()
    }.build()

    @Test fun downloadsAreCoalescedAndLeaseProtectsFileFromClear() = runBlocking {
        val count = AtomicInteger(); val dir = folder.newFolder()
        val store = ManhwaImageStore(dir, client(count))
        val leases = coroutineScope { (1..10).map { async { store.acquire(page(0), ASURA_CHAPTER) } }.awaitAll() }
        assertEquals(1, count.get()); assertArrayEquals(image, leases.first().file.readBytes())
        assertFalse(store.clear()); assertTrue(leases.first().file.isFile)
        leases.forEach { it.close(); it.close() }
        assertTrue(store.clear()); assertTrue(dir.listFiles().isNullOrEmpty())
    }
    @Test fun lruEvictsOnlyInactivePagesAndNeverExceedsBudget() = runBlocking {
        val count = AtomicInteger(); val dir = folder.newFolder()
        val store = ManhwaImageStore(dir, client(count), capacityBytes = 30, maxImageBytes = 20)
        val active = store.acquire(page(0), ASURA_CHAPTER)
        assertTrue(runCatching { store.acquire(page(1), ASURA_CHAPTER) }.isFailure)
        assertTrue(active.file.exists()); assertFalse(dir.listFiles()!!.any { it.name.endsWith(".part") })
        active.close()
        store.acquire(page(1), ASURA_CHAPTER).use { assertTrue(it.file.isFile) }
        assertTrue(dir.listFiles()!!.sumOf { it.length() } <= 30)
        assertFalse(active.file.exists())
    }
    @Test fun badImagesOversizedBodiesAndFailedDownloadsLeaveNoPartialFiles() = runBlocking {
        val dir = folder.newFolder(); val count = AtomicInteger()
        val invalid = ManhwaImageStore(dir, client(count) { "<html>not an image</html>".toByteArray() })
        assertTrue(runCatching { invalid.acquire(page(0), ASURA_CHAPTER) }.isFailure)
        assertTrue(dir.listFiles().isNullOrEmpty())
        val small = ManhwaImageStore(dir, client(count), capacityBytes = 30, maxImageBytes = 12)
        assertTrue(runCatching { small.acquire(page(0), ASURA_CHAPTER) }.isFailure)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }
    @Test fun forcedRefreshRequiresReleasedLeaseAndNeverDeletesActiveDecoderFile() = runBlocking {
        val count = AtomicInteger(); val store = ManhwaImageStore(folder.newFolder(), client(count))
        val lease = store.acquire(page(0), ASURA_CHAPTER)
        assertTrue(runCatching { store.acquire(page(0), ASURA_CHAPTER, true) }.isFailure)
        assertTrue(lease.file.isFile); assertEquals(1, count.get()); lease.close()
        store.acquire(page(0), ASURA_CHAPTER, true).close(); assertEquals(2, count.get())
    }
    @Test fun cancellationCancelsRequestAndDoesNotRetainAPartialDownload() = runBlocking {
        val dir = folder.newFolder(); val entered = CountDownLatch(1); val cancelled = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            entered.countDown()
            while (!chain.call().isCanceled()) Thread.sleep(5)
            cancelled.countDown(); throw java.io.IOException("cancelled")
        }.build()
        val store = ManhwaImageStore(dir, client)
        val request = async(Dispatchers.Default) { store.acquire(page(0), ASURA_CHAPTER) }
        assertTrue(withContext(Dispatchers.IO) { entered.await(2, TimeUnit.SECONDS) })
        request.cancelAndJoin()
        assertTrue(withContext(Dispatchers.IO) { cancelled.await(2, TimeUnit.SECONDS) })
        assertTrue(dir.listFiles().isNullOrEmpty())
    }
}
