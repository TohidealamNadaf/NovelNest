package com.novelnest.app.feature.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderCooldownNavigationTest {
    @Test fun failedChapterAllowsButtonsButNotSwipeAndLoadingStillBlocksBoth() {
        val failed = ReaderUiState(chapters = listOf(Chapter("1", "One"), Chapter("2", "Two")),
            currentChapterIndex = 1, errorMessage = "Temporarily unavailable")
        assertTrue(failed.canNavigateUsingControls(1))
        assertFalse(failed.canNavigateFrom(1))
        assertFalse(failed.canNavigateUsingControls(0))
        assertFalse(failed.copy(isChapterLoading = true).canNavigateUsingControls(1))
        assertFalse(failed.copy(errorMessage = null).canNavigateUsingControls(1))
    }
}
