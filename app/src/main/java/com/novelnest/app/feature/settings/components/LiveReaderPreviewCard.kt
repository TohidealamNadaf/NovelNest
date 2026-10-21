package com.novelnest.app.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.data.preferences.ReaderSettings

@Composable
fun LiveReaderPreviewCard(
    settings: ReaderSettings,
    modifier: Modifier = Modifier
) {
    val theme = settings.theme
    val textColor = theme.textColor.copy(alpha = settings.textBrightness)
    val secondaryColor = theme.secondaryTextColor.copy(alpha = settings.textBrightness)

    Card(
        colors = CardDefaults.cardColors(containerColor = theme.backgroundColor),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (theme.isDark) NovelSurfaceBorder else Color(0xFFD6C8B4)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Live Preview Header Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.surfaceColor)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "LIVE PREVIEW • ${theme.displayName}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NovelPrimary
                    )
                }

                Text(
                    text = "${settings.fontFamily.displayName} • ${settings.fontSizeSp.toInt()}sp",
                    fontSize = 11.sp,
                    color = secondaryColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Chapter Title
            Text(
                text = "Chapter 1: The Whispering Sanctuary",
                fontFamily = settings.fontFamily.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (settings.fontSizeSp + 3f).sp,
                lineHeight = ((settings.fontSizeSp + 3f) * settings.lineHeightMultiplier).sp,
                color = textColor
            )

            Spacer(modifier = Modifier.height(settings.paragraphSpacingDp.dp))

            // Paragraph 1
            Text(
                text = "The silver lantern cast long, dancing shadows across the mahogany desk. Ancient parchment whispered secrets of epochs past, each delicate stroke of ink holding memories that kingdoms had risen and fallen to protect.",
                fontFamily = settings.fontFamily.fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = settings.fontSizeSp.sp,
                lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                color = textColor
            )

            Spacer(modifier = Modifier.height(settings.paragraphSpacingDp.dp))

            // Paragraph 2
            Text(
                text = "Sunny drew a quiet breath, turning the aged page with reverent care. In this quiet refuge, the relentless storms of the mortal world faded into distant echoes.",
                fontFamily = settings.fontFamily.fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = settings.fontSizeSp.sp,
                lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                color = textColor
            )
        }
    }
}
