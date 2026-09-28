package com.kidfocus.timer.ui.navigation

/** Smart schedule always requires a parent PIN, including on devices with no PIN yet. */
internal fun smartScheduleGateRoute(hasPinSet: Boolean): String =
    if (hasPinSet) NavRoutes.PinEntry.buildRoute(NavRoutes.SmartSchedule.route) else NavRoutes.PinSetup.route
