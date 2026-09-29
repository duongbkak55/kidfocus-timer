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

    /** Parent account and cloud synchronization. */
    data object CloudSync : NavRoutes("cloud_sync")

    /** Schedule management screen (list of scheduled tasks). */
    data object Schedule : NavRoutes("schedule")

    data object QuickSchedule : NavRoutes("quick_schedule")

    data object DayLogs : NavRoutes("day_logs")
    data object WeeklyComparison : NavRoutes("weekly_comparison")

    data object SmartSchedule : NavRoutes("smart_schedule")

    /** Daily timeline view showing today's (or any day's) tasks. */
    data object DailySchedule : NavRoutes("daily_schedule")

    /** Task edit/create screen. Receives task id (0=new), type, and optional pre-filled hour/minute. */
    data object TaskEdit : NavRoutes("task_edit/{$ARG_TASK_ID}/{$ARG_TASK_TYPE}/{$ARG_HOUR}/{$ARG_MINUTE}") {
        fun buildRoute(taskId: Long, taskType: String, hour: Int = -1, minute: Int = -1) =
            "task_edit/$taskId/$taskType/$hour/$minute"
    }

    /** AI homework chat screen. */
    data object AiChat : NavRoutes("ai_chat")

    /** Offline learning game catalog shared with future iOS builds. */
    data object LearningHub : NavRoutes("learning_hub")

    /** Parent/child-friendly learning progress dashboard. */
    data object LearningProgress : NavRoutes("learning_progress")

    /** Parent-managed monthly/annual subscription. */
    data object Subscription : NavRoutes("subscription")

    /** Parent-only list of recurring deadline-based routines. */
    data object RoutineSettings : NavRoutes("routine_settings")

    /** Creates or edits a routine; id 0 represents a new routine. */
    data object RoutineEditor : NavRoutes("routine_editor/{$ARG_ROUTINE_ID}") {
        fun buildRoute(id: Long?) = "routine_editor/${id ?: 0L}"
    }

    /** Child-friendly, step-by-step player for today's routines. */
    data object RoutineRunner : NavRoutes("routine_runner")

    /** Schema-free sticker board and weekly routine summary. */
    data object RoutineProgress : NavRoutes("routine_progress")

    data object ChildProfilePicker : NavRoutes("child_profile_picker")

    data object ChildProfileSettings : NavRoutes("child_profile_settings")

    companion object {
        const val ARG_MINUTES = "minutes"
        const val ARG_DESTINATION = "destination"
        const val ARG_TASK_ID = "task_id"
        const val ARG_TASK_TYPE = "task_type"
        const val ARG_HOUR = "hour"
        const val ARG_MINUTE = "minute"
        const val ARG_ROUTINE_ID = "routineId"
    }
}
