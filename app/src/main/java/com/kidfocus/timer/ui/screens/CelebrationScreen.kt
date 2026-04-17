package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidfocus.timer.ui.components.CelebrationOverlay
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel

/**
 * Celebration screen with confetti overlay, shown after a focus session completes.
 *
 * @param focusMinutes Minutes focused in the session just completed.
 * @param onStartBreak Navigate to the break timer.
 * @param onGoHome Navigate back to the home screen.
 */
@Composable
fun CelebrationScreen(
    focusMinutes: Int,
    settingsViewModel: SettingsViewModel,
    onStartBreak: () -> Unit,
    onGoHome: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val settings by settingsViewModel.settings.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Confetti layer (non-blocking, transparent)
        CelebrationOverlay(
            particleCount = 150,
            durationMs = 4000,
        )

        // Content layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Tuyệt vời!",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp),
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "🎉",
                fontSize = 72.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Bạn vừa tập trung $focusMinutes phút!",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bạn thật giỏi! Hãy tiếp tục phát huy nhé!",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onBackground.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Action buttons
            val breakMinutes = settings?.breakDurationMinutes ?: 5

            Button(
                onClick = onStartBreak,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.breakArc),
            ) {
                Text(
                    text = "Nghỉ $breakMinutes phút",
                    style = MaterialTheme.typography.titleMedium,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = "Về trang chủ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
