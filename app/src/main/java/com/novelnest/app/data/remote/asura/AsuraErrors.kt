package com.novelnest.app.data.remote.asura

import java.io.IOException

sealed class AsuraException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Parse(message: String, cause: Throwable? = null) : AsuraException("Asura page format changed: $message", cause)
    class Blocked : AsuraException("Asura requires browser verification. Try again later.")
    class NotFound : AsuraException("This Asura series or chapter was not found (404).")
    class Locked(val unlockTime: String? = null) : AsuraException("This chapter is locked at Asura. Open it on the source website or wait until it becomes public.")
    class AccessRequired : AsuraException("Asura requires account access for this chapter. No public images were returned.")
    class Http(val status: Int, val retryAfter: String? = null) : AsuraException(
        if (status == 429) "Asura request limit reached. Wait before retrying." else "Asura returned HTTP $status. Retry later.")
    class Network(cause: IOException) : AsuraException("Unable to connect to Asura. Check your connection and retry.", cause)
    class TooLarge : AsuraException("Asura returned a response larger than the supported limit.")
}
