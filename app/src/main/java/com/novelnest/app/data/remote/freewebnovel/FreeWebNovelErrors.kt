package com.novelnest.app.data.remote.freewebnovel

import java.io.IOException

sealed class FreeWebNovelException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class ParseError(message: String, cause: Throwable? = null) : FreeWebNovelException(message, cause)
    class Blocked : FreeWebNovelException("FreeWebNovel requires browser verification. Please retry later.")
    class NotFound : FreeWebNovelException("Novel or chapter not found at FreeWebNovel (404)")
    class HttpError(val status: Int) : FreeWebNovelException("FreeWebNovel returned HTTP $status") {
        val retryable get() = status == 429 || status in 500..599
    }
    class NetworkError(cause: IOException) : FreeWebNovelException("Unable to connect to FreeWebNovel: ${cause.message}", cause)
    class IncompleteChapterList(message: String) : FreeWebNovelException(message)
}
