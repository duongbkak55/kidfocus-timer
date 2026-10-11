package com.kidfocus.timer.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kidfocus.timer.domain.model.TimerSettings
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
@Config(sdk = [31], application = Application::class, qualifiers = "en-rUS")
class HistoryEntryPointsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun bothStatsGoalAndReviewLinkOpenHistory() {
        var openings = 0
        compose.setContent { MaterialTheme {
            HistoryEntryPoints(30, 1, 1f) { openings++ }
        } }
        listOf("history_focus_minutes", "history_sessions_today", "history_daily_goal",
            "history_view_again").forEach { tag -> compose.onNodeWithTag(tag).performClick() }
        compose.runOnIdle { assertEquals(4, openings) }
    }

    @Test fun celebrationSecondaryButtonOpensHistory() {
        val settings = mockk<SettingsViewModel>()
        every { settings.settings } returns MutableStateFlow(TimerSettings())
        var openings = 0
        compose.setContent { MaterialTheme {
            CelebrationScreen(25, settings, onStartBreak = {}, onGoHome = {},
                onOpenFocusHistory = { openings++ })
        } }
        compose.onNodeWithText("View history").performClick()
        compose.runOnIdle { assertEquals(1, openings) }
    }
}
