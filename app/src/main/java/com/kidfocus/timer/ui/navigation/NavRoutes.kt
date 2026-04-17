package com.kidfocus.timer.ui.navigation

/**
 * Sealed hierarchy of all navigation routes in the app.
 *
 * Each object/class corresponds to exactly one destination in [AppNavigation].
 * Using a sealed class prevents typos and makes exhaustive `when` expressions possible.
 */
sealed class NavRoutes(val route: String) {

    /** First-run onboarding flow shown to new users. */
    data object Onboarding : NavRoutes("onboarding")

    /** PIN setup screen accessed during onboarding or from parent settings. */
    data object PinSetup : NavRoutes("pin_setup")

    /** Main home screen showing today's stats and start button. */
    data object Home : NavRoutes("home")

    /** Active focus countdown screen. */
    data object Focus : NavRoutes("focus")

    /** Active break countdown screen. */
    data object BreakTimer : NavRoutes("break_timer")

    /** Celebration screen shown after a focus session completes. Receives focused minutes. */
    data object Celebration : NavRoutes("celebration/{$ARG_MINUTES}") {
        fun buildRoute(minutes: Int) = "celebration/$minutes"
    }

    /** Color theme picker screen. */
    data object Theme : NavRoutes("theme")

    /**
     * PIN entry gate that precedes locked screens.
     * The [destination] argument encodes where to navigate on success.
     */
    data object PinEntry : NavRoutes("pin_entry/{$ARG_DESTINATION}") {
        fun buildRoute(destination: String) = "pin_entry/$destination"
    }

    /** Parent-only settings screen, accessible after PIN verification. */
    data object ParentSettings : NavRoutes("parent_settings")

    companion object {
        const val ARG_MINUTES = "minutes"
        const val ARG_DESTINATION = "destination"
    }
}
