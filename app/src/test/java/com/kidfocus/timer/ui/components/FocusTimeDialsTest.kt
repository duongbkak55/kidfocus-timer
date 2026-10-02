package com.kidfocus.timer.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.kidfocus.timer.domain.model.TimerPhase
import com.kidfocus.timer.domain.model.TimerSettings
import com.kidfocus.timer.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class FocusTimeDialsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun beforeStartButtonsMoveOnlyFiveMinutesAndDoNotPersistSettings() {
        val selected = mutableIntStateOf(30)
        compose.setContent { MaterialTheme { FocusDurationDial(selected.intValue, { selected.intValue = it }, Color.Blue) } }
        repeat(4) { compose.onNodeWithTag("focus_before_plus").performClick() }
        compose.runOnIdle { assertEquals(50, selected.intValue) }
        compose.onNodeWithTag("focus_before_minus").performClick()
        compose.runOnIdle { assertEquals(45, selected.intValue) }
    }

    @Test fun runningDialNeedsOneSecondHoldAndLocksAfterThreeSecondsWithNoMinusControl() {
        compose.mainClock.autoAdvance = false
        val state = TimerState(TimerPhase.Focus, 50 * 60, 49 * 60, isRunning = true)
        compose.setContent { MaterialTheme { FocusExtensionDial(state, TimerSettings(), Color.Blue, "49:00", {}) } }
        compose.onNodeWithTag("focus_dial_locked").assertExists()
        compose.onNodeWithText("−5").assertDoesNotExist()
        compose.onNodeWithTag("focus_extension_dial").performTouchInput { longClick(durationMillis = 800) }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("focus_dial_locked").assertExists()
        compose.onNodeWithTag("focus_extension_dial").performTouchInput { longClick(durationMillis = 1_100) }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("focus_dial_unlocked").assertExists()
        compose.mainClock.advanceTimeBy(3_100)
        compose.onNodeWithTag("focus_dial_locked").assertExists()
    }
}
