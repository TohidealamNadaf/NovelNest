package com.novelnest.app.feature.home

import com.novelnest.app.data.local.epub.LocalBookEntry

/** Home can resume local books without assigning them a website source or changing source tabs. */
sealed interface HomeContinueBook {
    data class Online(val entry: HomeReadingEntry) : HomeContinueBook
    data class Local(val entry: LocalBookEntry) : HomeContinueBook
}

internal fun selectContinueBook(online: HomeReadingEntry?, local: List<LocalBookEntry>): HomeContinueBook? {
    val recent = local.filter { it.position != null }.maxByOrNull { it.position!!.updatedAt }
    return when {
        recent != null && (online == null || recent.position!!.updatedAt > online.progress.lastReadTimestamp) -> HomeContinueBook.Local(recent)
        online != null -> HomeContinueBook.Online(online)
        else -> null
    }
}
