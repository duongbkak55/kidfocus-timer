package com.kidfocus.timer.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.kidfocus.timer.domain.model.FocusHistory
import com.kidfocus.timer.domain.model.FocusHistoryBar
import com.kidfocus.timer.domain.model.FocusHistoryDay
import com.kidfocus.timer.domain.model.FocusHistoryRow
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, qualifiers = "vi-rVN-w420dp-h850dp")
class FocusHistoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun emptyAndFilledDemoScreens() {
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val bars = (0L..6L).map { FocusHistoryBar(monday.plusDays(it), listOf(20, 30, 15, 0, 25, 0, 35)[it.toInt()]) }
        val empty = FocusHistory(bars.map { it.copy(minutes = 0) }, 0, 0, emptyList())
        val timestamp = today.atTime(16, 40).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val filled = FocusHistory(bars, bars.sumOf { it.minutes }, 5,
            listOf(FocusHistoryDay(today, listOf(
                FocusHistoryRow(2, timestamp, "Đọc sách", 1500, true, false),
                FocusHistoryRow(1, timestamp - 3_600_000, "Bài tập Toán", 600, false, false),
                FocusHistoryRow(null, timestamp - 7_200_000, "Đánh răng", null, true, true),
            ))))
        val current = mutableStateOf(empty)
        compose.setContent { MaterialTheme { FocusHistoryScreen(current.value, onBack = {}) } }
        compose.onNodeWithTag("history_empty").assertExists()
        compose.runOnIdle { current.value = filled }
        compose.waitForIdle()
        compose.onNodeWithTag("history_empty").assertDoesNotExist()
        compose.onNodeWithText("Đọc sách").assertExists()
    }
}
