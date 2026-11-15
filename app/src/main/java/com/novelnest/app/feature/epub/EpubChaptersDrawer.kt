package com.novelnest.app.feature.epub

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelBadgeBackground
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.NovelNestIconButton
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Link

/**
 * Separate Sidebar Drawer for the Local EPUB Reader displaying all chapters/sections,
 * matching the visual design of the online webnovel reader sidebar while remaining
 * independent from NovelPhoenix / FreeWebNovel components.
 */
@Composable
internal fun EpubChaptersDrawer(
    toc: List<Pair<Int, Link>>,
    currentChapterIndex: Int,
    onSelectChapter: (Link) -> Boolean,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    isOpen: Boolean = false
) {
    val activeChapterIndex = currentChapterIndex.takeIf { it in toc.indices }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = activeChapterIndex ?: 0)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isOpen, activeChapterIndex) {
        if (isOpen && activeChapterIndex != null) {
            listState.scrollToItem(activeChapterIndex)
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
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
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
                        text = "${toc.size} chapters" + (activeChapterIndex?.let { " - Current ${it + 1}" } ?: ""),
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

            // Quick Jump navigation bar (FIRST, CURRENT, LATEST)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // FIRST
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NovelSurfaceVariant,
                    border = BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardDoubleArrowUp,
                            contentDescription = null,
                            tint = NovelTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "FIRST",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NovelTextSecondary
                        )
                    }
                }

                // CURRENT
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NovelBadgeBackground,
                    border = BorderStroke(1.dp, NovelPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(((activeChapterIndex ?: 0) - 2).coerceAtLeast(0))
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CURRENT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NovelPrimary
                        )
                    }
                }

                // LATEST
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NovelSurfaceVariant,
                    border = BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem((toc.size - 1).coerceAtLeast(0))
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardDoubleArrowDown,
                            contentDescription = null,
                            tint = NovelTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "LATEST",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NovelTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                itemsIndexed(toc, key = { index, pair -> "${pair.second.href}:$index" }) { index, (depth, link) ->
                    val isCurrent = index == activeChapterIndex
                    val indentDp = (depth.coerceAtMost(3) * 10).dp

                    val cardBackground = when {
                        isCurrent -> NovelSurfaceVariant.copy(alpha = 0.7f)
                        else -> Color.Transparent
                    }

                    val cardBorder = when {
                        isCurrent -> NovelSurfaceBorder.copy(alpha = 0.8f)
                        else -> NovelSurfaceBorder.copy(alpha = 0.25f)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indentDp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(cardBackground)
                            .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) {
                                if (onSelectChapter(link)) onCloseDrawer()
                            }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val indexBg = NovelSurfaceVariant

                        val indexBorder = when {
                            isCurrent -> NovelSurfaceBorder.copy(alpha = 0.8f)
                            else -> NovelSurfaceBorder.copy(alpha = 0.5f)
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(indexBg)
                                .border(1.dp, indexBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) NovelPrimary else NovelTextPrimary
                                )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = link.title?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isCurrent -> NovelPrimary
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
