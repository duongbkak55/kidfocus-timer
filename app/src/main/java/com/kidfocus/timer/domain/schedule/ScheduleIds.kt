package com.kidfocus.timer.domain.schedule

import java.security.SecureRandom

object ScheduleIds {
    private val random = SecureRandom()

    // Random 52-bit IDs stay exact in JavaScript and avoid Room autoGenerate collisions across devices.
    @Synchronized
    fun newId(): Long = (random.nextLong() ushr 12) + 1
}
