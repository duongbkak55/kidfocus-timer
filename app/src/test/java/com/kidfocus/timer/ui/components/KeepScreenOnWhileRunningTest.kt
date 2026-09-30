package com.kidfocus.timer.ui.components

import android.app.Application
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class KeepScreenOnWhileRunningTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun flagTracksRunningPauseSettingAndLeavingScreen() {
        val running = mutableStateOf(true)
        val preference = mutableStateOf(true)
        val visible = mutableStateOf(true)
        lateinit var screenView: View
        compose.setContent {
            if (visible.value) {
                screenView = LocalView.current
                KeepScreenOnWhileRunning(running.value && preference.value)
            }
        }
        fun flagSet() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        compose.runOnIdle { assertTrue(screenView.keepScreenOn); assertTrue(flagSet()) }

        compose.runOnIdle { running.value = false }
        compose.runOnIdle { assertFalse(screenView.keepScreenOn); assertFalse(flagSet()) }

        compose.runOnIdle { running.value = true; preference.value = false }
        compose.runOnIdle { assertFalse(screenView.keepScreenOn); assertFalse(flagSet()) }

        compose.runOnIdle { preference.value = true }
        compose.runOnIdle { assertTrue(screenView.keepScreenOn); assertTrue(flagSet()) }

        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { assertFalse(screenView.keepScreenOn); assertFalse(flagSet()) }
    }
}
