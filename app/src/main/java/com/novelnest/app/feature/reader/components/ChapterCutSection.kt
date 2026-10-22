package com.novelnest.app.feature.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * End-of-chapter cut section.
 * Displays a cut border with space on left and right showing '---',
 * and a caption prompting the user: "Tap or Swipe Up for next chapter".
 */
@Composable
fun ChapterCutSection(
    hasNextChapter: Boolean,
    textColor: Color,
    secondaryColor: Color,
    onNextChapter: () -> Unit,
    nextChapterTitle: String? = null,
    modifier: Modifier = Modifier
) {
    val playHaptic = rememberNovelNestHaptic()
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = NovelPrimary),
                role = Role.Button,
                enabled = hasNextChapter,
                onClick = {
                    playHaptic(NovelNestHapticType.LIGHT_TICK)
                    onNextChapter()
                }
            )
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Cut section with border and space on left and right showing '---'
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = secondaryColor.copy(alpha = 0.35f),
                thickness = 1.dp
            )

            Text(
                text = "  ---  ",
                color = secondaryColor.copy(alpha = 0.6f),
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = secondaryColor.copy(alpha = 0.35f),
                thickness = 1.dp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Caption
        Text(
            text = if (hasNextChapter) {
                "Tap or Swipe Up for next chapter"
            } else {
                "You've reached the latest chapter"
            },
            color = if (hasNextChapter) NovelPrimary else secondaryColor.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = if (hasNextChapter) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
        if (hasNextChapter) {
            Spacer(modifier = Modifier.height(8.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = NovelPrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = nextChapterTitle?.takeIf { it.isNotBlank() } ?: "Next chapter",
                color = textColor,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
