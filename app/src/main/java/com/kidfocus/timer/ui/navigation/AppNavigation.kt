package com.kidfocus.timer.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.ui.screens.BreakScreen
import com.kidfocus.timer.ui.screens.CelebrationScreen
import com.kidfocus.timer.ui.screens.FocusScreen
import com.kidfocus.timer.ui.screens.HomeScreen
import com.kidfocus.timer.ui.screens.OnboardingScreen
import com.kidfocus.timer.ui.screens.ParentSettingsScreen
import com.kidfocus.timer.ui.screens.PinEntryScreen
import com.kidfocus.timer.ui.screens.DailyScheduleScreen
import com.kidfocus.timer.ui.screens.ScheduleScreen
import com.kidfocus.timer.ui.screens.TaskEditScreen
import com.kidfocus.timer.ui.screens.ThemeScreen
import com.kidfocus.timer.ui.viewmodel.ScheduleViewModel
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel
import com.kidfocus.timer.ui.viewmodel.TimerViewModel

/**
 * Root navigation graph for KidFocus Timer.
 *
 * [settingsViewModel] is hoisted from [MainActivity] so the app theme is already applied
 * before any screen is rendered. All other ViewModels are created per-destination.
 *
 * Start destination is determined by whether onboarding has been completed.
 */
@Composable
fun AppNavigation(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val settings by settingsViewModel.settings.collectAsState()

    val startDestination = if (settings?.onboardingCompleted == true) {
        NavRoutes.Home.route
    } else {
        NavRoutes.Onboarding.route
    }

    // Shared TimerViewModel scoped to the nav graph so Focus and Break screens share state
    val timerViewModel: TimerViewModel = hiltViewModel()
    val scheduleViewModel: ScheduleViewModel = hiltViewModel()

    Box(modifier = modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {

        // ---- Onboarding -----------------------------------------------------------------------
        composable(NavRoutes.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    settingsViewModel.completeOnboarding()
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }

        // ---- PIN Setup ------------------------------------------------------------------------
        composable(NavRoutes.PinSetup.route) {
            PinEntryScreen(
                isSetupMode = true,
                settingsViewModel = settingsViewModel,
                onSuccess = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }

        // ---- Home -----------------------------------------------------------------------------
        composable(NavRoutes.Home.route) {
            HomeScreen(
                timerViewModel = timerViewModel,
                settingsViewModel = settingsViewModel,
                scheduleViewModel = scheduleViewModel,
                onStartFocus = { navController.navigate(NavRoutes.Focus.route) },
                onOpenTheme = { navController.navigate(NavRoutes.Theme.route) },
                onOpenDailySchedule = { navController.navigate(NavRoutes.DailySchedule.route) },
                onOpenParentSettings = {
                    val current = settingsViewModel.settings.value
                    if (current?.hasPinSet == true) {
                        navController.navigate(
                            NavRoutes.PinEntry.buildRoute(NavRoutes.ParentSettings.route)
                        )
                    } else {
                        navController.navigate(NavRoutes.ParentSettings.route)
                    }
                },
            )
        }

        // ---- Focus ----------------------------------------------------------------------------
        composable(NavRoutes.Focus.route) {
            FocusScreen(
                timerViewModel = timerViewModel,
                settingsViewModel = settingsViewModel,
                onSessionComplete = { minutes ->
                    navController.navigate(NavRoutes.Celebration.buildRoute(minutes)) {
                        popUpTo(NavRoutes.Focus.route) { inclusive = true }
                    }
                },
                onStop = {
                    timerViewModel.stop()
                    navController.popBackStack()
                },
            )
        }

        // ---- Break ----------------------------------------------------------------------------
        composable(NavRoutes.BreakTimer.route) {
            BreakScreen(
                timerViewModel = timerViewModel,
                settingsViewModel = settingsViewModel,
                onBreakComplete = {
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.BreakTimer.route) { inclusive = true }
                    }
                },
                onSkip = {
                    timerViewModel.stop()
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.BreakTimer.route) { inclusive = true }
                    }
                },
            )
        }

        // ---- Celebration ----------------------------------------------------------------------
        composable(
            route = NavRoutes.Celebration.route,
            arguments = listOf(
                navArgument(NavRoutes.ARG_MINUTES) { type = NavType.IntType }
            ),
        ) { backStack ->
            val minutes = backStack.arguments?.getInt(NavRoutes.ARG_MINUTES) ?: 0
            CelebrationScreen(
                focusMinutes = minutes,
                settingsViewModel = settingsViewModel,
                onStartBreak = {
                    navController.navigate(NavRoutes.BreakTimer.route) {
                        popUpTo(NavRoutes.Celebration.route) { inclusive = true }
                    }
                },
                onGoHome = {
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.Celebration.route) { inclusive = true }
                    }
                },
            )
        }

        // ---- Theme ----------------------------------------------------------------------------
        composable(NavRoutes.Theme.route) {
            ThemeScreen(
                settingsViewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        // ---- PIN Entry (gate) ----------------------------------------------------------------
        composable(
            route = NavRoutes.PinEntry.route,
            arguments = listOf(
                navArgument(NavRoutes.ARG_DESTINATION) { type = NavType.StringType }
            ),
        ) { backStack ->
            val destination = backStack.arguments?.getString(NavRoutes.ARG_DESTINATION)
                ?: NavRoutes.ParentSettings.route
            PinEntryScreen(
                isSetupMode = false,
                settingsViewModel = settingsViewModel,
                onSuccess = {
                    navController.navigate(destination) {
                        popUpTo(NavRoutes.PinEntry.route) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }

        // ---- Parent Settings -----------------------------------------------------------------
        composable(NavRoutes.ParentSettings.route) {
            ParentSettingsScreen(
                settingsViewModel = settingsViewModel,
                onBack = {
                    settingsViewModel.resetPinVerification()
                    navController.popBackStack()
                },
                onSetPin = { navController.navigate(NavRoutes.PinSetup.route) },
                onOpenSchedule = { navController.navigate(NavRoutes.Schedule.route) },
            )
        }

        // ---- Schedule ------------------------------------------------------------------------
        composable(NavRoutes.Schedule.route) {
            ScheduleScreen(
                viewModel = scheduleViewModel,
                onBack = { navController.popBackStack() },
                onEditTask = { task ->
                    navController.navigate(NavRoutes.TaskEdit.buildRoute(task.id, task.taskType.name))
                },
                onNewTask = { taskType ->
                    navController.navigate(NavRoutes.TaskEdit.buildRoute(0L, taskType.name))
                },
            )
        }

        // ---- Daily Schedule ------------------------------------------------------------------
        composable(NavRoutes.DailySchedule.route) {
            DailyScheduleScreen(
                viewModel = scheduleViewModel,
                onBack = { navController.popBackStack() },
                onStartTask = { task ->
                    val seconds = task.focusDurationMinutes * 60
                    timerViewModel.startFocus(seconds)
                    navController.navigate(NavRoutes.Focus.route)
                },
            )
        }

        // ---- Task Edit -----------------------------------------------------------------------
        composable(
            route = NavRoutes.TaskEdit.route,
            arguments = listOf(
                navArgument(NavRoutes.ARG_TASK_ID) { type = NavType.LongType },
                navArgument(NavRoutes.ARG_TASK_TYPE) { type = NavType.StringType },
            ),
        ) { backStack ->
            val taskId = backStack.arguments?.getLong(NavRoutes.ARG_TASK_ID) ?: 0L
            val taskTypeName = backStack.arguments?.getString(NavRoutes.ARG_TASK_TYPE) ?: TaskType.CUSTOM.name
            val taskType = runCatching { TaskType.valueOf(taskTypeName) }.getOrDefault(TaskType.CUSTOM)
            val tasks by scheduleViewModel.tasks.collectAsState()
            val existing = tasks.find { it.id == taskId }
            val task = existing ?: scheduleViewModel.taskFromType(taskType)
            TaskEditScreen(
                task = task,
                viewModel = scheduleViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
    } // end Box
}
