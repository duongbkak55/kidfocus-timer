package com.kidfocus.timer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kidfocus.timer.ui.theme.KidFocusTheme

/** A local photo when available, otherwise the task's familiar emoji. */
@Composable
fun TaskVisual(
    photoUri: String?,
    emoji: String,
    modifier: Modifier = Modifier,
    emojiSize: TextUnit = 24.sp,
) {
    val colors = KidFocusTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.primary.copy(alpha = 0.10f))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (photoUri.isNullOrBlank()) {
            Text(text = emoji, fontSize = emojiSize)
        } else {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
