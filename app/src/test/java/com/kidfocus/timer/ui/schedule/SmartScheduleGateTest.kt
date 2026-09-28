package com.kidfocus.timer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartScheduleGateTest {
    @Test fun `missing PIN requires setup`() {
        assertEquals(NavRoutes.PinSetup.route, smartScheduleGateRoute(false))
    }
    @Test fun `existing PIN requires verification for smart schedule`() {
        assertEquals(NavRoutes.PinEntry.buildRoute(NavRoutes.SmartSchedule.route), smartScheduleGateRoute(true))
    }
}
