package com.novelnest.app.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSecondary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick

/**
 * Solid, borderless category card for the main Settings view.
 * Features:
 * - 100% solid NovelSurface background with no borders or glassmorphism
 * - Refined solid icon badge in NovelSurfaceVariant (no borders)
 * - Cohesive typography and solid pill value chips
 * - Tactile micro-bounce feedback on press
 */
@Composable
fun SettingsCategoryTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    valueBadge: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(18.dp)

    Card(
        colors = CardDefaults.cardColors(containerColor = NovelSurface),
        shape = cardShape,
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .bounceClick(
                scaleDown = BounceScale.Medium,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Solid, borderless icon container
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NovelSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NovelPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NovelTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NovelTextSecondary,
                    fontSize = 11.sp
                )
                if (valueBadge != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NovelSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = valueBadge,
                            color = NovelSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = NovelTextSecondary.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
