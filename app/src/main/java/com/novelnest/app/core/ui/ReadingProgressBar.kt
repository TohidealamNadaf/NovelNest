package com.novelnest.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.data.progress.ReadingCompletion

@Composable
fun ReadingProgressBar(completion: ReadingCompletion, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(completion.label, style = MaterialTheme.typography.labelMedium, color = NovelTextSecondary)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { completion.fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = NovelPrimary, trackColor = NovelSurfaceBorder,
            gapSize = 0.dp, drawStopIndicator = {})
    }
}
