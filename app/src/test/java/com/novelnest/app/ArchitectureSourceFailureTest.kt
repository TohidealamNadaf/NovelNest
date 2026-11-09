package com.novelnest.app

import com.novelnest.app.data.model.NovelSourceType
import com.novelnest.app.data.remote.novelphoenix.NovelPhoenixSource
import com.novelnest.app.data.remote.network.HostRateLimiter
import com.novelnest.app.data.repository.NovelRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ArchitectureSourceFailureTest {
    private fun source(status: Int, html: String) = NovelPhoenixSource(
        client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status)
                .message("Fixture").body(html.toResponseBody("text/html".toMediaType())).build()
        }.build(), rateLimiter = HostRateLimiter(0))

    @Test fun blockedPhoenixFeedDoesNotBecomeSuccessfulSampleData() = runTest {
        assertTrue(source(403, "Blocked").getHomeSections().isFailure)
    }
    @Test fun missingSelectorsAreReportedInsteadOfReturningSampleCards() = runTest {
        assertTrue(source(200, "<html><body>Changed listing</body></html>").getHomeSections().isFailure)
    }
    @Test fun missingSourceDoesNotGenerateFeedSearchOrIdentityRecords() = runTest {
        val repository = NovelRepositoryImpl(sources = emptyMap())
        val source = NovelSourceType.NOVEL_PHOENIX
        assertTrue(repository.getHomeSections(source).first().isFailure)
        assertTrue(repository.searchNovels(source, "Shadow Slave").isFailure)
        assertNull(repository.getNovelById("np-1"))
    }
}
