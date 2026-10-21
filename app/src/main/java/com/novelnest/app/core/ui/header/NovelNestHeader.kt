package com.novelnest.app.core.ui.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.IconButton
import androidx.compose.ui.tooling.preview.Preview
import com.novelnest.app.core.designsystem.NovelBackground
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary

/**
 * Reusable, modular Top Header component for NovelNest screens.
 * Supports custom branding icon, title, optional subtitle, and trailing action slots.
 */
@Composable
fun NovelNestHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NovelPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = NovelTextPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = NovelTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            actions()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun NovelNestHeaderDefaultPreview() {
    NovelNestHeader(
        title = "NovelNest",
        subtitle = "Your Calm Reading Sanctuary",
        icon = Icons.Rounded.AutoStories,
        modifier = Modifier.background(NovelBackground)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1216)
@Composable
private fun NovelNestHeaderWithActionPreview() {
    NovelNestHeader(
        title = "Discover",
        subtitle = "Explore novels and manhwas",
        icon = Icons.Rounded.Search,
        modifier = Modifier.background(NovelBackground),
        actions = {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Rounded.BookmarkBorder,
                    contentDescription = "Bookmarks",
                    tint = NovelTextSecondary
                )
            }
        }
    )
}
