package com.novelnest.app.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.rememberAreAnimationsDisabled
import com.novelnest.app.core.ui.footer.rememberFooterBottomPadding

/**
 * Dedicated sub-pages for categorized Settings navigation.
 */
enum class SettingsSubPage {
    MAIN,
    READING_DISPLAY,
    READING_BEHAVIOR,
    AUDIO_NARRATION,
    APP_EXPERIENCE,
    NOTIFICATIONS,
    STORAGE_CACHE
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val readerSettings by viewModel.readerSettings.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val cacheSize by viewModel.cacheSizeFormatted.collectAsState()
    val clearSuccessMessage by viewModel.clearCacheSuccessMessage.collectAsState()

    var currentSubPage by remember { mutableStateOf(SettingsSubPage.MAIN) }
    var showResetDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val areAnimationsDisabled = rememberAreAnimationsDisabled()

    // Handle physical / gesture back navigation seamlessly
    BackHandler(enabled = currentSubPage != SettingsSubPage.MAIN) {
        currentSubPage = SettingsSubPage.MAIN
    }

    LaunchedEffect(clearSuccessMessage) {
        clearSuccessMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissClearCacheMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        AnimatedContent(
            targetState = currentSubPage,
            transitionSpec = {
                if (areAnimationsDisabled) {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                } else if (targetState == SettingsSubPage.MAIN) {
                    (slideInHorizontally(animationSpec = tween(280)) { -it / 2 } + fadeIn(animationSpec = tween(280)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(280)) { it } + fadeOut(animationSpec = tween(200)))
                } else {
                    (slideInHorizontally(animationSpec = tween(280)) { it } + fadeIn(animationSpec = tween(280)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(280)) { -it / 2 } + fadeOut(animationSpec = tween(200)))
                }
            },
            label = "settings_subpage_transition"
        ) { page ->
            when (page) {
                SettingsSubPage.MAIN -> {
                    SettingsMainView(
                        readerSettings = readerSettings,
                        appSettings = appSettings,
                        cacheSize = cacheSize,
                        onNavigateTo = { currentSubPage = it },
                        onResetRequested = { showResetDialog = true }
                    )
                }

                SettingsSubPage.READING_DISPLAY -> {
                    ReadingDisplayPage(
                        readerSettings = readerSettings,
                        viewModel = viewModel,
                        onBack = { currentSubPage = SettingsSubPage.MAIN },
                        onResetRequested = { showResetDialog = true }
                    )
                }

                SettingsSubPage.READING_BEHAVIOR -> {
                    ReadingBehaviorPage(
                        readerSettings = readerSettings,
                        viewModel = viewModel,
                        onBack = { currentSubPage = SettingsSubPage.MAIN }
                    )
                }

                SettingsSubPage.AUDIO_NARRATION -> {
                    AudioNarrationPage(
                        onBack = { currentSubPage = SettingsSubPage.MAIN }
                    )
                }

                SettingsSubPage.APP_EXPERIENCE -> {
                    AppExperiencePage(
                        appSettings = appSettings,
                        viewModel = viewModel,
                        onBack = { currentSubPage = SettingsSubPage.MAIN }
                    )
                }

                SettingsSubPage.NOTIFICATIONS -> {
                    NotificationSettingsPage(
                        onBack = { currentSubPage = SettingsSubPage.MAIN }
                    )
                }

                SettingsSubPage.STORAGE_CACHE -> {
                    StorageCachePage(
                        cacheSize = cacheSize,
                        clearCacheSuccessMessage = clearSuccessMessage,
                        onClearCache = { viewModel.clearCache() },
                        onDismissMessage = { viewModel.dismissClearCacheMessage() },
                        onBack = { currentSubPage = SettingsSubPage.MAIN }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = rememberFooterBottomPadding() + 8.dp)
        )

        // Reset Defaults Confirmation Dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                containerColor = NovelSurface,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = "Reset Reading Settings?",
                        color = NovelTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This will restore font size (17sp), Classic Serif, balanced line spacing, and Obsidian dark theme.",
                        color = NovelTextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetReadingDefaults()
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NovelPrimary,
                            contentColor = NovelOnPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Reset Defaults",
                            color = NovelOnPrimary,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel", color = NovelTextSecondary)
                    }
                }
            )
        }
    }
}
