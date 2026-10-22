package com.novelnest.app.feature.reader

/** Content text is not a row identity: repeated text and String hash collisions are valid. */
internal fun readerParagraphKey(chapterId: String, paragraphIndex: Int): String =
    "paragraph:$chapterId:$paragraphIndex"

/** Keep metadata for every chapter, but retain full text only around the current one. */
internal fun retainReaderContent(
    chapters: List<Chapter>,
    currentIndex: Int,
    radius: Int = 2
): List<Chapter> = chapters.mapIndexed { index, chapter ->
    if (index < currentIndex - radius || index > currentIndex + radius) {
        if (chapter.paragraphs.isEmpty()) chapter else chapter.copy(paragraphs = emptyList())
    } else chapter
}

/** An outgoing or still-loading chapter cannot consume another navigation gesture. */
internal fun ReaderUiState.canNavigateFrom(expectedChapterIndex: Int): Boolean =
    !isLoading && !isChapterLoading && currentChapterIndex == expectedChapterIndex &&
        chapters.getOrNull(currentChapterIndex)?.paragraphs?.isNotEmpty() == true
