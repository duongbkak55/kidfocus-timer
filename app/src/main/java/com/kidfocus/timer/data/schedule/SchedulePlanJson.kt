package com.kidfocus.timer.data.schedule

import com.kidfocus.timer.domain.schedule.PlanKind
import com.kidfocus.timer.domain.schedule.SchedulePlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONObject

object SchedulePlanJson {
    fun toMap(plan: SchedulePlan): Map<String, Any> = mapOf(
        "kind" to plan.kind.name, "startedOn" to plan.startedOn.toString(), "target" to plan.target.toString(),
        "initial" to plan.initial.mapKeys { it.key.name }.mapValues { it.value.toString() },
        "nextStep" to plan.nextStep, "nextOn" to plan.nextOn.toString(),
    )
    fun fromMap(raw: Map<*, *>): SchedulePlan = SchedulePlan(
        PlanKind.valueOf(raw["kind"] as String), LocalDate.parse(raw["startedOn"] as String),
        (raw["initial"] as Map<*, *>).entries.associate { DayOfWeek.valueOf(it.key as String) to LocalTime.parse(it.value as String) },
        LocalTime.parse(raw["target"] as String), (raw["nextStep"] as Number).toInt(), LocalDate.parse(raw["nextOn"] as String),
    ).also { it.validate() }
    fun encode(plan: SchedulePlan): String = JSONObject(toMap(plan)).toString()
    fun decode(raw: String): SchedulePlan {
        val json = JSONObject(raw)
        val initial = json.getJSONObject("initial")
        return fromMap(json.keys().asSequence().associateWith { if (it == "initial") initial.keys().asSequence().associateWith(initial::getString) else json.get(it) })
    }
}
