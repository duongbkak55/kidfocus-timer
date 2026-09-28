package com.kidfocus.timer.domain.schedule

import java.time.DayOfWeek

/** Calendar uses Sunday=1; routine bit 0 is Monday. */
object DayCodec {
    fun toCalendar(day: DayOfWeek): Int = day.value % 7 + 1
    fun fromCalendar(day: Int): DayOfWeek {
        require(day in 1..7)
        return DayOfWeek.of(if (day == 1) 7 else day - 1)
    }
    fun toMask(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day ->
        mask or (1 shl (day.value - 1))
    }
    fun fromMask(mask: Int): Set<DayOfWeek> {
        require(mask in 0..127)
        return DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
    }
    fun fromCalendar(days: Set<Int>): Set<DayOfWeek> = days.map(::fromCalendar).toSet()
    fun toCalendar(days: Set<DayOfWeek>): Set<Int> = days.map(::toCalendar).toSet()
}
