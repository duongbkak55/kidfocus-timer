package com.kidfocus.timer.domain.daylog

import com.kidfocus.timer.domain.schedule.ScheduleThresholds
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/** References and dated plan occurrences are transient; UUIDs/profile IDs never enter the request. */
data class DayLogRequest(val payload: Map<String, Any>, val plans: Map<String, DayPlanItem>)
data class DayLogCandidate(val date: LocalDate, val planRef: String?, val name: String, val category: DayLogCategory,
    val startMinute: Int, val endMinute: Int?, val confidence: Double) {
    val selectedByDefault get() = confidence >= 0.6
    fun isFutureAt(now: LocalDateTime): Boolean = date.atStartOfDay().plusMinutes(startMinute.toLong()).isAfter(now)
}
data class DayLogPreview(val entries: List<DayLogCandidate>, val questions: List<String>)
object DayLogDraft {
    fun request(text: String, date: LocalDate, tasks: List<com.kidfocus.timer.domain.model.ScheduledTask>, anchors: com.kidfocus.timer.domain.schedule.ScheduleAnchors, ageBand: String, requestId: String): DayLogRequest {
        require(text.isNotBlank() && text.length <= 2000)
        val dates = if (Regex("hôm qua|tối qua", RegexOption.IGNORE_CASE).containsMatchIn(text)) listOf(date.minusDays(1), date) else listOf(date)
        val plans = dates.flatMap { planForDate(it, tasks, anchors) }.take(180).mapIndexed { i, p -> "p$i" to p }.toMap()
        fun time(minute: Int) = "%02d:%02d".format(Locale.ROOT, minute / 60, minute % 60)
        return DayLogRequest(mapOf("mode" to "LOG", "requestId" to requestId, "text" to text, "date" to date.toString(),
            "ageBand" to (ageBand.takeIf { it in ScheduleThresholds.sleepMinutes } ?: "4-5"),
            "locale" to Locale.getDefault().let { it.language + (it.country.takeIf { c -> c.length == 2 }?.let { c -> "-$c" } ?: "") },
            "plans" to plans.map { (ref, p) -> mapOf("ref" to ref, "date" to p.date.toString(), "name" to p.name.take(60),
                "category" to p.category.name, "start" to time(p.startMinute), "durationMin" to p.durationMinutes) }), plans)
    }
    fun fromMap(raw: Map<*, *>, request: DayLogRequest): DayLogPreview {
        require(raw.keys == setOf("entries", "questions"))
        val rows = raw["entries"] as List<*>; val questions = raw["questions"] as List<*>
        require(rows.size <= 30 && questions.size <= 30)
        fun minute(v: Any?): Int { val s = v as String; require(s.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]"))); return LocalTime.parse(s).let { it.hour * 60 + it.minute } }
        val reference = LocalDate.parse(request.payload.getValue("date") as String)
        val entries = rows.map { row ->
            val e = row as Map<*, *>; require(e.keys == setOf("date", "planRef", "name", "category", "start", "end", "confidence"))
            val date = LocalDate.parse(e["date"] as String); require(date in reference.minusDays(1)..reference.plusDays(1))
            val ref = e["planRef"] as String?; val plan = ref?.let { checkNotNull(request.plans[it]) }
            val category = DayLogCategory.valueOf(e["category"] as String)
            require(plan == null || plan.category == category && (plan.date == date ||
                plan.date.plusDays(1) == date && minute(e["start"]) < 360 && plan.startMinute >= 1080))
            val name = (e["name"] as String).trim(); require(name.length in 1..60)
            val confidence = (e["confidence"] as Number).toDouble(); require(confidence.isFinite() && confidence in 0.0..1.0)
            DayLogCandidate(date, ref, name, category, minute(e["start"]), e["end"]?.let(::minute), confidence)
        }
        require(entries.map { listOf(it.date,it.planRef,it.name,it.category,it.startMinute,it.endMinute) }.distinct().size == entries.size)
        return DayLogPreview(entries, questions.map { q -> (q as String).also { require(it.trim().isNotEmpty() && it.length <= 300) } })
    }
}
