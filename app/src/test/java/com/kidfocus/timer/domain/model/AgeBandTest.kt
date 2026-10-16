package com.kidfocus.timer.domain.model

import com.kidfocus.timer.domain.schedule.ScheduleThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeBandTest {
    @Test fun `ids are ordered for both profile and learning pickers`() {
        assertEquals(listOf("2-3", "4-5", "5-6", "l1", "l2", "l3"), AgeBand.orderedIds)
        assertTrue(AgeBand.isValid("5-6"))
        assertFalse(AgeBand.isValid("unknown"))
        assertEquals("5-6", AgeBand.normalize("5-6"))
        assertEquals(AgeBand.DEFAULT_ID, AgeBand.normalize("unknown"))
    }

    @Test fun `five to six uses preschool sleep and focus thresholds`() {
        assertEquals(ScheduleThresholds.sleepMinutes["4-5"], ScheduleThresholds.sleepMinutes["5-6"])
        assertEquals(ScheduleThresholds.focusMinutes["4-5"], ScheduleThresholds.focusMinutes["5-6"])
    }
}
