package com.kidfocus.timer.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kidfocus.timer.domain.model.FocusHistory
import com.kidfocus.timer.domain.model.FocusHistoryBar
import com.kidfocus.timer.domain.model.FocusHistoryDay
import com.kidfocus.timer.domain.model.FocusHistoryRow
import com.kidfocus.timer.ui.screens.FocusHistoryScreen
import com.kidfocus.timer.ui.theme.KidFocusTheme
import androidx.compose.ui.Modifier
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device screenshots use only synthetic Bé Na examples; no account or stored child data is read. */
@RunWith(AndroidJUnit4::class)
class FocusHistoryImagesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun saveEmptyAndFilledScreens() {
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
        compose.setContent { KidFocusTheme { Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            FocusHistoryScreen(current.value, onBack = {})
        } } }
        capture("empty.png")
        compose.runOnIdle { current.value = filled }
        compose.waitForIdle()
        capture("filled.png")
    }

    private fun capture(name: String) {
        val output = File(compose.activity.getExternalFilesDir(null), "focus-history-screenshots/$name")
        output.parentFile?.mkdirs()
        output.outputStream().use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
