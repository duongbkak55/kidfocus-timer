package com.kidfocus.timer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kidfocus.timer.data.cloud.CloudAccount
import com.kidfocus.timer.ui.theme.KidFocusTheme

@Composable
fun AccountAvatar(
    account: CloudAccount,
    size: Dp,
    modifier: Modifier = Modifier,
    showOnlineDot: Boolean = false,
) {
    val colors = KidFocusTheme.colors
    val initial = account.displayName?.trim()?.firstOrNull()
        ?: account.email?.trim()?.firstOrNull()
        ?: '?'

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial.uppercase(),
                color = colors.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!account.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = account.photoUrl,
                    contentDescription = "Ảnh tài khoản ${account.displayName ?: account.email.orEmpty()}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(CircleShape),
                )
            }
        }
        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.28f).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E)),
            )
        }
    }
}
