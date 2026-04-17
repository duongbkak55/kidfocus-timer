package com.kidfocus.timer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.ui.components.PinDotRow
import com.kidfocus.timer.ui.components.PinKeyboard
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel

private const val PIN_LENGTH = 4

/**
 * PIN entry screen used both for setup (creating a new PIN) and verification (unlocking).
 *
 * @param isSetupMode When true, the user is setting a new PIN and must confirm it.
 * @param onSuccess Called after successful verification or PIN confirmation.
 * @param onCancel Called when the user presses back.
 */
@Composable
fun PinEntryScreen(
    isSetupMode: Boolean,
    settingsViewModel: SettingsViewModel,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val pinVerified by settingsViewModel.pinVerified.collectAsState()
    val pinError by settingsViewModel.pinError.collectAsState()

    var digits by remember { mutableStateOf("") }
    var confirmDigits by remember { mutableStateOf("") }
    var isConfirmStep by remember { mutableStateOf(false) }
    var setupMismatch by remember { mutableStateOf(false) }

    // Navigate on success
    LaunchedEffect(pinVerified) {
        if (pinVerified) {
            settingsViewModel.resetPinVerification()
            onSuccess()
        }
    }

    val title = when {
        isSetupMode && isConfirmStep -> "Nhập lại PIN"
        isSetupMode -> "Tạo PIN mới"
        else -> "Nhập PIN phụ huynh"
    }

    val subtitle = when {
        setupMismatch -> "PIN không khớp, thử lại"
        pinError -> "PIN không đúng"
        isSetupMode && isConfirmStep -> "Nhập lại PIN vừa tạo để xác nhận"
        isSetupMode -> "Tạo mã PIN 4 chữ số để bảo vệ cài đặt"
        else -> "Vui lòng nhập PIN để tiếp tục"
    }

    val subtitleColor = when {
        setupMismatch || pinError -> Color(0xFFEF4444)
        else -> colors.onBackground.copy(alpha = 0.6f)
    }

    val currentDigits = if (isConfirmStep) confirmDigits else digits
    val showError = pinError || setupMismatch

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Back button
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Quay lại",
                tint = colors.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "\uD83D\uDD12",
                style = MaterialTheme.typography.displaySmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            PinDotRow(
                filledCount = currentDigits.length,
                totalDigits = PIN_LENGTH,
                errorState = showError,
            )

            Spacer(modifier = Modifier.height(40.dp))

            PinKeyboard(
                onDigit = { digit ->
                    setupMismatch = false
                    if (isConfirmStep) {
                        if (confirmDigits.length < PIN_LENGTH) {
                            val updated = confirmDigits + digit.toString()
                            confirmDigits = updated
                            if (updated.length == PIN_LENGTH) {
                                if (updated == digits) {
                                    settingsViewModel.savePin(updated)
                                    settingsViewModel.resetPinVerification()
                                    onSuccess()
                                } else {
                                    setupMismatch = true
                                    confirmDigits = ""
                                }
                            }
                        }
                    } else {
                        if (digits.length < PIN_LENGTH) {
                            val updated = digits + digit.toString()
                            digits = updated
                            if (updated.length == PIN_LENGTH) {
                                if (isSetupMode) {
                                    isConfirmStep = true
                                } else {
                                    settingsViewModel.verifyPin(updated)
                                    if (pinError) digits = ""
                                }
                            }
                        }
                    }
                },
                onDelete = {
                    setupMismatch = false
                    if (isConfirmStep) {
                        if (confirmDigits.isNotEmpty()) confirmDigits = confirmDigits.dropLast(1)
                    } else {
                        if (digits.isNotEmpty()) digits = digits.dropLast(1)
                    }
                },
            )
        }
    }
}
