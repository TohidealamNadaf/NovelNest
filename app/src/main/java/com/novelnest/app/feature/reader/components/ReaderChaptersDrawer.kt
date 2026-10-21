package com.novelnest.app.feature.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.designsystem.NovelTextTertiary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.feature.reader.Chapter

/**
 * Sidebar drawer for Reader Screen displaying all chapters.
 * - Opens via swipe-right gesture from left edge or top bar menu action.
 * - Wraps long chapter titles naturally without truncation.
 * - Displays rounded square cards: green checkmark on subtle green background when read,
 *   or chapter index number when unread.
 * - Dims read chapter titles for clear progression awareness.
 */
@Composable
fun ReaderChaptersDrawer(
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    readChapterIds: Set<String>,
    onSelectChapter: (Int) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Scroll to current active chapter when opened
    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex in chapters.indices) {
            val scrollIndex = (currentChapterIndex - 2).coerceAtLeast(0)
            listState.scrollToItem(scrollIndex)
        }
    }

    ModalDrawerSheet(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight(),
        drawerContainerColor = NovelSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Chapters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NovelTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${chapters.size} chapters • ${readChapterIds.size} read",
                        style = MaterialTheme.typography.bodySmall,
                        color = NovelTextSecondary,
                        fontSize = 11.sp
                    )
                }

                NovelNestIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close chapters drawer",
                    tint = NovelTextSecondary,
                    onClick = onCloseDrawer
                )
            }

            HorizontalDivider(
                color = NovelSurfaceBorder.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            // Chapter list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(chapters, key = { _, ch -> ch.id }) { index, chapter ->
                    val isCurrent = index == currentChapterIndex
                    val isRead = readChapterIds.contains(chapter.id)

                    val cardBackground = when {
                        isCurrent -> NovelPrimary.copy(alpha = 0.12f)
                        isRead -> Color(0xFF13171D)
                        else -> NovelSurfaceVariant.copy(alpha = 0.6f)
                    }

                    val borderColor = when {
                        isCurrent -> NovelPrimary.copy(alpha = 0.7f)
                        isRead -> Color(0x3322C55E)
                        else -> NovelSurfaceBorder.copy(alpha = 0.4f)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(cardBackground)
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) {
                                onSelectChapter(index)
                                onCloseDrawer()
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Square card with rounded corner for index number / green checkmark
                        val squareBg = if (isRead) {
                            Color(0x2E22C55E) // Subtle green tint
                        } else {
                            Color(0xFF1C222B)
                        }

                        val squareBorder = if (isRead) {
                            Color(0x6622C55E)
                        } else {
                            NovelSurfaceBorder
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(squareBg)
                                .border(1.dp, squareBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isRead) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Read",
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) NovelPrimary else NovelTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Chapter title - wraps naturally across lines without truncation
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isCurrent -> NovelPrimary
                                isRead -> NovelTextTertiary.copy(alpha = 0.65f) // Dimmed text
                                else -> NovelTextPrimary
                            },
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
