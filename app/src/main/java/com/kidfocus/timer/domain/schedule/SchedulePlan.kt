package com.kidfocus.timer.domain.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.sign

enum class PlanKind { BED, WAKE }

/** One parent-approved plan per profile. Calendar dates avoid midnight/time-zone timer drift. */
data class SchedulePlan(
    val kind: PlanKind,
    val startedOn: LocalDate,
    val initial: Map<DayOfWeek, LocalTime>,
    val target: LocalTime,
    val nextStep: Int = 1,
    val nextOn: LocalDate = startedOn,
) {
    val totalSteps: Int get() = initial.values.maxOf { (abs(timeDifference(it, target)) + STEP_MINUTES - 1) / STEP_MINUTES }
    fun timesAt(step: Int): Map<DayOfWeek, LocalTime> = initial.mapValues { (_, start) ->
        val delta = timeDifference(start, target)
        timeAtMinute(start.minutes() + delta.sign * minOf(abs(delta), step * STEP_MINUTES))
    }
    fun matches(anchors: ScheduleAnchors): Boolean {
        val times = if (kind == PlanKind.BED) anchors.bed else anchors.wake
        return timesAt(nextStep - 1).all { (day, time) -> times[day] == time }
    }
    fun due(today: LocalDate): Boolean = today >= nextOn
    fun change(): ScheduleChange = if (kind == PlanKind.BED) ScheduleChange.SetBed(timesAt(nextStep)) else ScheduleChange.SetWake(timesAt(nextStep))
    fun advanced(today: LocalDate): SchedulePlan? = if (nextStep >= totalSteps) null else copy(nextStep = nextStep + 1, nextOn = today.plusDays(STEP_DAYS))
    fun validate() {
        require(initial.isNotEmpty() && totalSteps in 1..48 && nextStep in 1..totalSteps)
        require(nextOn >= startedOn)
    }
    companion object {
        const val STEP_MINUTES = 15
        const val STEP_DAYS = 3L
        const val DIRECT_LIMIT_MINUTES = 30
        fun create(kind: PlanKind, days: Set<DayOfWeek>, target: LocalTime, anchors: ScheduleAnchors, today: LocalDate): SchedulePlan {
            val times = if (kind == PlanKind.BED) anchors.bed else anchors.wake
            return SchedulePlan(kind, today, days.associateWith { checkNotNull(times[it]) }, target).also { it.validate() }
        }
    }
}

/** Shortest signed distance, including 00:15 → 23:45. */
fun timeDifference(from: LocalTime, to: LocalTime): Int = Math.floorMod(to.minutes() - from.minutes() + 720, 1440) - 720
