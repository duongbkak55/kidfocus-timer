package com.kidfocus.timer.domain.schedule

import android.app.Application
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ScheduleAdviceGoldenTest {
    @Test fun `12 Vietnamese golden constraints use Android rule engine and exact Apply dry run`() {
        val file = listOf(File("functions/test/fixtures/schedule-advise.vi.json"), File("../functions/test/fixtures/schedule-advise.vi.json")).first { it.exists() }
        val cases = JSONObject(file.readText()).getJSONArray("cases")
        assertTrue(cases.length() >= 10)
        val useCase = ApplyScheduleUseCase(object : ScheduleStore {
            override suspend fun read(profileId: String): ScheduleState = error("No storage in dry run")
            override suspend fun snapshot(profileId: String): ScheduleSnapshot? = error("No storage in dry run")
            override suspend fun saveSnapshot(profileId: String, snapshot: ScheduleSnapshot?) = error("No storage in dry run")
            override suspend fun replace(profileId: String, expected: ScheduleState, state: ScheduleState) = error("No storage in dry run")
            override fun reschedule(before: List<ScheduledTask>, after: List<ScheduledTask>) = error("No alarms in dry run")
        })
        for (index in 0 until cases.length()) {
            val fixture = cases.getJSONObject(index)
            val input = fixture.getJSONObject("input")
            fun days(raw: JSONArray) = (0 until raw.length()).map { d -> DayOfWeek.entries.single { it.name.take(3) == raw.getString(d) } }.toSet()
            fun times(raw: JSONObject) = raw.keys().asSequence().associate { key -> DayOfWeek.entries.single { it.name.take(3) == key } to LocalTime.parse(raw.getString(key)) }
            val a = input.getJSONObject("anchors")
            val school = a.getJSONArray("school")
            val anchors = ScheduleAnchors(times(a.getJSONObject("wake")), times(a.getJSONObject("bed")), (0 until school.length()).map { i -> school.getJSONObject(i).let { Block(days(it.getJSONArray("days")), LocalTime.parse(it.getString("start")), LocalTime.parse(it.getString("end")), "Trường") } })
            val rawTasks = input.getJSONArray("tasks")
            val tasks = (0 until rawTasks.length()).map { i -> rawTasks.getJSONObject(i).let { t ->
                val start = LocalTime.parse(t.getString("start"))
                ScheduledTask(i + 1L, TaskType.valueOf(t.getString("taskType")), t.getString("name"), "", start.hour, start.minute, DayCodec.toCalendar(days(t.getJSONArray("days"))), t.getInt("durationMin"), 0)
            } }
            val before = ScheduleState(tasks, anchors)
            val advice = ScheduleAdvicePayload.advice(map(fixture.getJSONObject("expected")))
            val rows = ScheduleAdviceReview.review(advice, ScheduleAdviceReview.refs(before), "default", before, input.getString("ageBand"), useCase)
            assertTrue(fixture.getString("id"), rows.all { it.rejection == null })
            val after = useCase.previewAdvice("default", before, rows.flatMap { it.changes }, input.getString("ageBand"))
            val findings = ScheduleAdvisor().advise(after.tasks, after.anchors, input.getString("ageBand"))
            val fixes = fixture.getJSONObject("constraints").getJSONArray("mustFix")
            for (i in 0 until fixes.length()) assertTrue(fixture.getString("id") + " " + fixes.getString(i), findings.none { it.ruleId.name == fixes.getString(i) })
            assertEquals(anchors.school, after.anchors.school)
        }
    }
    private fun map(json: JSONObject): Map<String, Any?> = json.keys().asSequence().associateWith { unwrap(json.get(it)) }
    private fun unwrap(value: Any?): Any? = when (value) { is JSONObject -> map(value); is JSONArray -> (0 until value.length()).map { unwrap(value.get(it)) }; else -> value }
}
