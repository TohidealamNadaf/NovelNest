package com.novelnest.app.feature.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.AnimatedBookmarkButton
import com.novelnest.app.data.preferences.ReaderTheme

/**
 * Top Reading Control Header.
 * Properly placed with status bar insets, clean typography, bookmarking, and typography customization.
 */
@Composable
fun ReaderTopBar(
    novelTitle: String,
    chapterTitle: String,
    currentTheme: ReaderTheme,
    textColor: Color,
    secondaryColor: Color,
    isVisible: Boolean,
    isBookmarked: Boolean = false,
    isSyncing: Boolean = false,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleBookmark: () -> Unit = {},
    onResyncChapter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(280)
        ),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(220)
        ),
        modifier = modifier
    ) {
        Surface(
            color = currentTheme.surfaceColor,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Back button + Novel / Chapter Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        NovelNestIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Leave reading mode",
                            tint = textColor,
                            onClick = onBack
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Column {
                            Text(
                                text = novelTitle.ifBlank { "NovelNest" },
                                color = textColor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chapterTitle.isNotBlank()) {
                                Text(
                                    text = chapterTitle,
                                    color = secondaryColor,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Right: Actions (Resync + Bookmark + Typography Settings)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSyncing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = NovelPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        } else {
                            NovelNestIconButton(
                                icon = Icons.Rounded.Refresh,
                                contentDescription = "Resync chapter content",
                                tint = secondaryColor,
                                onClick = onResyncChapter
                            )
                        }

                        AnimatedBookmarkButton(
                            isBookmarked = isBookmarked,
                            onToggle = { onToggleBookmark() }
                        )

                        NovelNestIconButton(
                            icon = Icons.Rounded.FormatSize,
                            contentDescription = "Adjust reader settings",
                            tint = NovelPrimary,
                            onClick = onOpenSettings
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = NovelSurfaceBorder.copy(alpha = 0.5f)
                )
            }
        }
    }
}
