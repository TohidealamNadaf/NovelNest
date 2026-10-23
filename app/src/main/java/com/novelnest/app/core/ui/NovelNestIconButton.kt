package com.novelnest.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic

/**
 * Standard tactile icon button for NovelNest.
 * Guarantees a minimum 48dp touch target for accessibility, tactile spring bounce,
 * soft indication feedback, and mandatory contentDescription.
 * When [isAvatar] is true or [imageUri] is provided, renders a standard 34dp profile avatar container
 * with a refined border and graceful fallback.
 */
@Composable
fun NovelNestIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NovelTextPrimary,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
    imageUri: String? = null,
    avatarSize: Dp = 34.dp,
    isAvatar: Boolean = imageUri != null
) {
    val playHaptic = rememberNovelNestHaptic()
    var imageLoaded by remember(imageUri) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            }
            .bounceClick(
                scaleDown = BounceScale.Small,
                enabled = enabled,
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = {
                    playHaptic(NovelNestHapticType.LIGHT_TICK)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isAvatar) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(NovelSurfaceVariant)
                    .border(
                        width = if (imageUri != null && imageLoaded) 1.5.dp else 1.dp,
                        color = if (imageUri != null && imageLoaded) NovelPrimary.copy(alpha = 0.7f) else NovelSurfaceBorder,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onSuccess = { imageLoaded = true },
                        onError = { imageLoaded = false },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clearAndSetSemantics { }
                    )
                }
                if (imageUri == null || !imageLoaded) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (imageUri != null) NovelPrimary else NovelTextSecondary,
                        modifier = Modifier
                            .size(19.dp)
                            .clearAndSetSemantics { }
                    )
                }
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(iconSize)
                    .clearAndSetSemantics { }
            )
        }
    }
}
