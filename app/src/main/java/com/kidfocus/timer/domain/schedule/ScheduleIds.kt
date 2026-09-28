package com.kidfocus.timer.domain.schedule

import kotlin.random.Random

object ScheduleIds {
    // Avoid same-millisecond collisions within this process as well as between devices.
    private var lastId = 0L
    @Synchronized
    fun newId(): Long {
        val candidate = System.currentTimeMillis() * 1000 + Random.nextInt(1000)
        lastId = maxOf(candidate, lastId + 1)
        return lastId
    }
}
