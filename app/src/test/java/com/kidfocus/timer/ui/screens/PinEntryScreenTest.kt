package com.kidfocus.timer.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class PinEntryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun wrongPinClearsDigitsSoNextFourDigitsCanBeEntered() {
        val viewModel = mockk<SettingsViewModel>()
        val error = MutableStateFlow(false)
        val verified = MutableStateFlow(false)
        val attempts = mutableListOf<String>()
        every { viewModel.pinError } returns error
        every { viewModel.pinVerified } returns verified
        every { viewModel.verifyPin(any()) } answers {
            attempts += firstArg<String>()
            error.value = true
        }
        every { viewModel.resetPinVerification() } answers { error.value = false }

        compose.setContent {
            KidFocusTheme {
                PinEntryScreen(false, viewModel, onSuccess = {}, onCancel = {})
            }
        }

        repeat(4) { compose.onNodeWithText("1").performClick() }
        repeat(4) { compose.onNodeWithText("2").performClick() }
        compose.runOnIdle { assertEquals(listOf("1111", "2222"), attempts) }
    }
}
