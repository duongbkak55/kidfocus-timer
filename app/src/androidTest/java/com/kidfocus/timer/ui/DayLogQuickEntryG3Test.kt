package com.kidfocus.timer.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kidfocus.timer.BuildConfig
import com.kidfocus.timer.MainActivity
import com.kidfocus.timer.R
import com.kidfocus.timer.data.remote.*
import com.kidfocus.timer.data.repository.DayLogRepository
import com.kidfocus.timer.domain.daylog.*
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.ui.navigation.*
import com.kidfocus.timer.ui.screens.*
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in Pixel acceptance test. Only the test APK supplies the offline AI/config fixture.
 * Uses the real parent gate, screens, ViewModels and existing Room data; never signs in.
 * Run with -e appPin <existing app PIN>, with an unlocked device and blank Firebase build.
 */
@RunWith(AndroidJUnit4::class)
class DayLogQuickEntryG3Test {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun typedPreviewSaveUndoUpdatesRealComparison() {
        val pin = InstrumentationRegistry.getArguments().getString("appPin").orEmpty()
        assumeTrue("Opt-in test needs the existing app PIN", pin.matches(Regex("[0-9]{4,8}")))
        assertTrue("This acceptance test must not contact Firebase", BuildConfig.FIREBASE_API_KEY.isBlank())
        assertTrue(BuildConfig.FIREBASE_APP_ID.isBlank() && BuildConfig.FIREBASE_PROJECT_ID.isBlank())
        val calls = AtomicInteger()
        val sentence = "Hôm nay làm bài từ 21h08 đến 21h55 và đọc sách từ 23h15 đến 23h45."
        val missingSentence = "Ngủ lúc 22h, làm bài mất 1 tiếng rưỡi"
        val today = LocalDate.now()
        val now = LocalTime.now()
        val nowMinute = now.hour * 60 + now.minute
        val graphReady = AtomicBoolean()
        lateinit var dayLogs: DayLogViewModel
        lateinit var quick: QuickDayLogViewModel
        lateinit var nav: NavHostController
        compose.activityRule.scenario.onActivity { activity ->
            val provider = ViewModelProvider(activity)
            dayLogs = provider[DayLogViewModel::class.java]
            val settings = provider[SettingsViewModel::class.java]
            val alarm = provider[ScheduleAlarmPermissionViewModel::class.java]
            // Access the already injected repository only in androidTest; no main APK hook.
            val field = DayLogViewModel::class.java.getDeclaredField("logs").apply { isAccessible = true }
            val repository = field.get(dayLogs) as DayLogRepository
            val logger = object : ScheduleLogger {
                override suspend fun log(request: DayLogRequest): ScheduleLogReply {
                    assertEquals(today.toString(), request.payload["date"])
                    calls.incrementAndGet()
                    if (request.payload["text"] == missingSentence) {
                        // Actual validator output generated offline from log-vi-29; test assets only.
                        val asset = if ((request.payload["locale"] as String).startsWith("en")) "schedule-log-g3-missing-en.json" else "schedule-log-g3-missing.json"
                        val fixture = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
                            .open(asset).bufferedReader().use { it.readText() })
                        val rows = fixture.getJSONArray("entries")
                        val mapped = (0 until rows.length()).map { i ->
                            val row = rows.getJSONObject(i)
                            row.keys().asSequence().associateWith { key -> if (key == "date") today.toString() else row.get(key).takeUnless { it == JSONObject.NULL } }
                        }
                        val questions = fixture.getJSONArray("questions")
                        return ScheduleLogReply(DayLogDraft.fromMap(mapOf("entries" to mapped,
                            "questions" to (0 until questions.length()).map(questions::getString)), request), AiUsage(8, 8, false))
                    }
                    assertEquals(sentence, request.payload["text"])
                    val due = dayLogs.data.value.tasks.associateBy { it.id }
                    val homework = request.plans.entries.single { (_, p) -> p.date == today && due[p.taskId]?.taskType == TaskType.HOMEWORK }
                    val reading = request.plans.entries.single { (_, p) -> p.date == today && due[p.taskId]?.taskType == TaskType.READING }
                    fun row(ref: String, plan: DayPlanItem, start: String, end: String) = mapOf(
                        "date" to today.toString(), "planRef" to ref, "name" to plan.name,
                        "category" to plan.category.name, "start" to start, "end" to end, "confidence" to 0.95,
                    )
                    val preview = DayLogDraft.fromMap(mapOf("entries" to listOf(
                        row(homework.key, homework.value, "21:08", "21:55"),
                        row(reading.key, reading.value, "23:15", "23:45")), "questions" to emptyList<String>()), request)
                    return ScheduleLogReply(preview, AiUsage(9, 9, false, remainingScheduleParses = 9))
                }
            }
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = QuickDayLogViewModel(logger, repository) as T
            }
            quick = ViewModelProvider(activity, factory)["w5b-offline-log", QuickDayLogViewModel::class.java]
            dayLogs.selectDate(today)
            dayLogs.thisWeek()
            activity.setContent {
                ParentSessionLifecycle(settings)
                val value by settings.settings.collectAsState()
                if (value != null) {
                    nav = rememberNavController()
                    graphReady.set(true)
                    KidFocusTheme { Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                        NavHost(nav, startDestination = NavRoutes.Home.route) {
                            composable(NavRoutes.Home.route) { Text("G3 offline fixture") }
                            parentPinEntry(nav, settings)
                            parentScheduleDestination(NavRoutes.DayLogs.route, nav, settings) {
                                DayLogsScreen(onBack = { nav.popBackStack() }, onStartTask = {}, dayLogs = dayLogs, alarmPermission = alarm,
                                    onCompare = { nav.navigate(NavRoutes.WeeklyComparison.route) }, quickEntry = { data, date ->
                                        QuickDayLogPanel(quick, data, date, AiConfig(scheduleEnabled = true), signedIn = true)
                                    })
                            }
                            parentScheduleDestination(NavRoutes.WeeklyComparison.route, nav, settings) {
                                WeeklyComparisonScreen(dayLogs) { nav.popBackStack() }
                            }
                        }
                    } }
                }
            }
        }
        compose.waitUntil(15_000) { graphReady.get() && dayLogs.data.value.profileId != null }
        compose.runOnIdle { nav.navigate(NavRoutes.DayLogs.route) }
        compose.onNodeWithText(label(R.string.pin_verify_title)).assertIsDisplayed()
        compose.onNodeWithTag("log_text").assertDoesNotExist()
        pin.forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.waitUntil(10_000) { nav.currentDestination?.route == NavRoutes.DayLogs.route }
        val before = dayLogs.data.value.entries
        fun comparison() = dayLogs.data.value.let { compareWeek(DayLogViewModel.monday(today), it.tasks, it.anchors, it.entries, today, nowMinute).summary }
        val baseline = comparison()
        screenshot("01-parent-daylogs")
        compose.onNodeWithText(label(R.string.log_today_title)).performScrollTo().performClick()
        compose.onNodeWithTag("log_text").performScrollTo().performTextInput(sentence)
        compose.onNodeWithText(compose.activity.resources.getQuantityString(R.plurals.log_preview, 1, 1)).performScrollTo().performClick()
        compose.waitUntil(10_000) { quick.state.value.preview?.entries?.size == 2 && !quick.state.value.busy }
        assertEquals(1, calls.get())
        assertEquals(setOf(0, 1), quick.state.value.selected)
        compose.onNodeWithTag("log_candidate_0").performScrollTo().assertIsOn()
        screenshot("02-two-matched-preview")
        compose.onNodeWithTag("log_save").performScrollTo().performClick()
        compose.waitUntil(10_000) { quick.state.value.undoBatch.size == 2 && !quick.state.value.busy && dayLogs.data.value.entries.size == before.size + 2 }
        val batch = quick.state.value.undoBatch
        assertTrue(batch.all { it.source == DayLogSource.AI && it.taskId != null && it.profileId == dayLogs.data.value.profileId })
        assertTrue(before.all { it in dayLogs.data.value.entries })
        screenshot("03-saved-with-undo")
        compose.onNodeWithText(label(R.string.daylog_compare_title)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.daylog_compare_title)).assertIsDisplayed()
        val after = comparison()
        assertNotEquals("Comparison must reflect the newly completed reading", baseline, after)
        screenshot("04-comparison-after-save")
        compose.onNodeWithContentDescription(label(R.string.back)).performClick()
        compose.onNodeWithTag("log_undo").performScrollTo().performClick()
        compose.waitUntil(10_000) { quick.state.value.restored && !quick.state.value.busy && dayLogs.data.value.entries.filter { it.id in batch.map(DayLogEntry::id) }.all { it.deleted } }
        assertEquals(before, dayLogs.data.value.entries.filter { it.id !in batch.map(DayLogEntry::id) })
        assertEquals(baseline, comparison())
        compose.onNodeWithText(label(R.string.log_restored)).performScrollTo().assertIsDisplayed()
        screenshot("05-undone")
        compose.onNodeWithText(label(R.string.daylog_compare_title)).performScrollTo().performClick()
        screenshot("06-comparison-restored")
        compose.onNodeWithContentDescription(label(R.string.back)).performClick()
        compose.onNodeWithTag("log_text").performScrollTo().performTextReplacement(missingSentence)
        compose.onNodeWithText(compose.activity.resources.getQuantityString(R.plurals.log_preview, 1, 1)).performScrollTo().performClick()
        compose.waitUntil(10_000) { quick.state.value.preview?.questions?.isNotEmpty() == true && !quick.state.value.busy }
        val partial = quick.state.value.preview!!
        assertEquals(1, partial.entries.size)
        assertEquals(DayLogCategory.SLEEP, partial.entries.single().category)
        val question = partial.questions.single { it.contains("bắt đầu lúc mấy giờ") || it.contains("what time did this activity start") }
        compose.onNodeWithText(question).performScrollTo().assertIsDisplayed()
        screenshot("07-missing-start-question")
        assertEquals(before, dayLogs.data.value.entries.filter { it.id !in batch.map(DayLogEntry::id) })
        assertEquals(baseline, comparison()) // Preview alone writes nothing.
        File(output(), "result.json").writeText(JSONObject().put("fixture", "offline provider and account/config, androidTest only")
            .put("date", today.toString()).put("previewEntries", 2).put("savedSource", "AI").put("providerCalls", calls.get())
            .put("beforePercent", baseline.completedPercent).put("afterPercent", after.completedPercent)
            .put("undoPercent", comparison().completedPercent).put("existingEntriesPreserved", true)
            .put("comparisonRestored", true).put("batchTombstones", 2).put("missingStartQuestionVisible", true).put("partialEntries", 1).toString(2))
    }

    private fun label(id: Int) = compose.activity.getString(id)
    private fun output() = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "g3-w5b").apply { mkdirs() }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        requireNotNull(bitmap)
        File(output(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
