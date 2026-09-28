package com.kidfocus.timer.data.remote

import org.junit.Assert.*
import org.junit.Test

class ScheduleImageAccessTest {
    @Test fun `old or missing config and signed out accounts hide photo action`() {
        assertFalse(AiConfig(scheduleEnabled = true).canImportImage(true))
        val configured = AiConfig(scheduleEnabled = true, scheduleImageTiers = setOf("early", "premium"), usage = AiUsage(15, 15, false, "early"))
        assertFalse(configured.canImportImage(false))
        assertFalse(configured.copy(scheduleEnabled = false).canImportImage(true))
        assertFalse(configured.copy(enabled = false).canImportImage(true))
    }
    @Test fun `photo action follows server tiers and free is hidden by beta config`() {
        for (tier in listOf("free", "guest", "early", "premium")) {
            val config = AiConfig(scheduleEnabled = true, scheduleImageTiers = setOf("early", "premium"), usage = AiUsage(15, 15, tier == "premium", tier))
            assertEquals(tier in setOf("early", "premium"), config.canImportImage(true))
            if (tier == "free") assertTrue(config.copy(scheduleImageTiers = setOf("free")).canImportImage(true))
        }
    }
}
