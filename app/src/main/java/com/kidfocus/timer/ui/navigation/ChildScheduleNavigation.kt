package com.kidfocus.timer.ui.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.ui.screens.DailyScheduleScreen
import com.kidfocus.timer.ui.viewmodel.*

/** Real child entry routes: adding a session never opens a weekly editor directly. */
internal fun NavGraphBuilder.childDailyScheduleDestinations(
    navController: NavHostController,
    scheduleViewModel: ScheduleViewModel,
    dayLogs: DayLogViewModel,
    timerViewModel: TimerViewModel,
    settingsViewModel: SettingsViewModel,
    alarmPermission: ScheduleAlarmPermissionViewModel,
    quickEntryEnabled: Boolean,
) {
    composable(NavRoutes.DailySchedule.route) {
        val dayLogData by dayLogs.data.collectAsState()
        val settings by settingsViewModel.settings.collectAsState()
        DailyScheduleScreen(
            viewModel = scheduleViewModel,
            actualEntries = dayLogData.entries,
            onChildQuickLog = { navController.navigate(NavRoutes.ChildQuickLog.route) },
            onActualAndComparison = { date ->
                dayLogs.selectDate(date)
                navController.navigate(parentGateRoute(settings?.hasPinSet == true, NavRoutes.DayLogs.route))
            },
            alarmPermission = alarmPermission,
            quickEntryEnabled = quickEntryEnabled,
            onQuickEntry = { navController.navigate(parentGateRoute(settings?.hasPinSet == true, NavRoutes.QuickSchedule.route)) },
            onBack = { navController.popBackStack() },
            onStartTask = { task ->
                if (task.taskType == TaskType.LEARNING_GAMES) {
                    navController.navigate(NavRoutes.LearningHub.route)
                } else {
                    timerViewModel.startFocusForTask(task)
                    navController.navigate(NavRoutes.Focus.route)
                }
            },
            onSkipTask = dayLogs::skipTaskToday,
            onUndoSkip = dayLogs::undoSkip,
            onAddTaskAtTime = { _, _ ->
                navController.navigate(NavRoutes.TodayActivity.route)
            },
        )
    }

    composable(NavRoutes.TodayActivity.route) {
        com.kidfocus.timer.ui.screens.TodayActivityScreen(
            onBack = { navController.popBackStack() },
            onStart = { name, minutes ->
                timerViewModel.startFocusForActivity(name, minutes * 60)
                navController.navigate(NavRoutes.Focus.route) {
                    popUpTo(NavRoutes.TodayActivity.route) { inclusive = true }
                }
            },
            onWeekly = {
                navController.navigate(NavRoutes.TaskEdit.buildRoute(0L, TaskType.CUSTOM.name))
            },
        )
    }


}
