package com.kidfocus.timer.ui.navigation

import androidx.activity.compose.BackHandler
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
import com.kidfocus.timer.BuildConfig
import com.kidfocus.timer.ui.screens.AiChatScreen
import com.kidfocus.timer.ui.screens.BreakScreen
import com.kidfocus.timer.ui.screens.CelebrationScreen
import com.kidfocus.timer.ui.screens.CloudSyncScreen
import com.kidfocus.timer.ui.screens.FocusScreen
import com.kidfocus.timer.ui.screens.HomeScreen
import com.kidfocus.timer.ui.screens.LearningHubScreen
import com.kidfocus.timer.ui.screens.LearningProgressScreen
import com.kidfocus.timer.ui.screens.OnboardingScreen
import com.kidfocus.timer.ui.screens.ParentSettingsScreen
import com.kidfocus.timer.ui.screens.PinEntryScreen
import com.kidfocus.timer.ui.screens.DailyScheduleScreen
import com.kidfocus.timer.ui.screens.DayLogsScreen
import com.kidfocus.timer.ui.screens.SmartScheduleScreen
import com.kidfocus.timer.ui.screens.ScheduleScreen
import com.kidfocus.timer.ui.screens.TaskEditScreen
import com.kidfocus.timer.ui.screens.SubscriptionScreen
import com.kidfocus.timer.ui.screens.ThemeScreen
import com.kidfocus.timer.ui.screens.RoutineEditorScreen
import com.kidfocus.timer.ui.screens.RoutineSettingsScreen
import com.kidfocus.timer.ui.screens.RoutineProgressScreen
import com.kidfocus.timer.ui.screens.RoutineRunnerScreen
import com.kidfocus.timer.ui.viewmodel.RoutineViewModel
import com.kidfocus.timer.ui.viewmodel.CloudSyncViewModel
import com.kidfocus.timer.ui.viewmodel.ScheduleViewModel
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel
import com.kidfocus.timer.ui.viewmodel.TimerViewModel
import com.kidfocus.timer.ui.viewmodel.ChildProfileViewModel
import com.kidfocus.timer.ui.screens.ChildProfilePickerScreen
import com.kidfocus.timer.ui.screens.ChildProfileSettingsScreen

/**
 * Returns the route that must be opened before entering a parent-only destination.
 * A configured PIN is always verified; a device without a PIN must create one first.
 */
internal fun parentGateRoute(hasPinSet: Boolean, destination: String): String =
    if (hasPinSet) NavRoutes.PinEntry.buildRoute(destination) else NavRoutes.PinSetup.route

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
    ParentSessionLifecycle(settingsViewModel)
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
    val alarmPermission: com.kidfocus.timer.ui.viewmodel.ScheduleAlarmPermissionViewModel = hiltViewModel()
    com.kidfocus.timer.ui.components.ScheduleAlarmPermissionLifecycle(alarmPermission)
    val dayLogs: com.kidfocus.timer.ui.viewmodel.DayLogViewModel = hiltViewModel()
    val routineViewModel: RoutineViewModel = hiltViewModel()
    val cloudSyncViewModel: CloudSyncViewModel = hiltViewModel()
    val scheduleAccess: com.kidfocus.timer.ui.viewmodel.ScheduleAccessViewModel = hiltViewModel()
    val scheduleConfig by scheduleAccess.config.collectAsState()
    val cloudAccount by cloudSyncViewModel.account.collectAsState()
    val cloudSyncStatus by cloudSyncViewModel.syncStatus.collectAsState()
    val childProfileViewModel: ChildProfileViewModel = hiltViewModel()
    val activeChildProfile by childProfileViewModel.activeProfile.collectAsState()

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
                routineViewModel = routineViewModel,
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
                onOpenAiChat = {
                    val current = settingsViewModel.settings.value
                    if (current?.hasPinSet == true) {
                        navController.navigate(NavRoutes.PinEntry.buildRoute(NavRoutes.AiChat.route))
                    } else {
                        navController.navigate(NavRoutes.PinSetup.route)
                    }
                },
                onOpenLearning = { navController.navigate(NavRoutes.LearningHub.route) },
                onOpenCloudSync = { navController.navigate(NavRoutes.CloudSync.route) },
                onStartRoutine = { navController.navigate(NavRoutes.RoutineRunner.route) },
                onOpenRoutineProgress = { navController.navigate(NavRoutes.RoutineProgress.route) },
                cloudAccount = cloudAccount,
                cloudSyncStatus = cloudSyncStatus,
                childProfile = activeChildProfile,
                onOpenProfilePicker = { navController.navigate(NavRoutes.ChildProfilePicker.route) },
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
        parentPinEntry(navController, settingsViewModel)

        // ---- Parent Settings -----------------------------------------------------------------
        composable(NavRoutes.ParentSettings.route) {
            BackHandler {
                settingsViewModel.lockParentSession()
                navController.popBackStack()
            }
            ParentSettingsScreen(
                settingsViewModel = settingsViewModel,
                onBack = {
                    settingsViewModel.lockParentSession()
                    navController.popBackStack()
                },
                onSetPin = { navController.navigate(NavRoutes.PinSetup.route) },
                onOpenSchedule = { navController.navigate(NavRoutes.Schedule.route) },
                onOpenSmartSchedule = { navController.navigate(NavRoutes.SmartSchedule.route) },
                onOpenRoutineSettings = { navController.navigate(NavRoutes.RoutineSettings.route) },
                onOpenCloudSync = { navController.navigate(NavRoutes.CloudSync.route) },
                onOpenSubscription = { navController.navigate(NavRoutes.Subscription.route) },
                onOpenChildProfiles = { navController.navigate(NavRoutes.ChildProfileSettings.route) },
                cloudAccount = cloudAccount,
                cloudSyncStatus = cloudSyncStatus,
            )
        }

        composable(NavRoutes.CloudSync.route) {
            CloudSyncScreen(
                viewModel = cloudSyncViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.Subscription.route) {
            SubscriptionScreen(
                onBack = { navController.popBackStack() },
                onOpenLogin = { navController.navigate(NavRoutes.CloudSync.route) },
            )
        }

        parentScheduleDestinations(
            navController = navController,
            settingsViewModel = settingsViewModel,
            smartScreen = {
                SmartScheduleScreen(onBack = { navController.popBackStack() },
                    onQuickEntry = { navController.navigate(NavRoutes.QuickSchedule.route) }, access = scheduleAccess,
                    alarmPermission = alarmPermission)
            },
            quickScreen = {
                com.kidfocus.timer.ui.screens.QuickScheduleScreen(onBack = { navController.popBackStack() }, access = scheduleAccess)
            },
        )

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
                onActualAndComparison = { date ->
                    dayLogs.selectDate(date)
                    navController.navigate(parentGateRoute(settings?.hasPinSet == true, NavRoutes.DayLogs.route))
                },
                alarmPermission = alarmPermission,
                quickEntryEnabled = scheduleConfig.scheduleEnabled,
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
                onAddTaskAtTime = { hour, minute ->
                    navController.navigate(NavRoutes.TaskEdit.buildRoute(0L, TaskType.CUSTOM.name, hour, minute))
                },
            )
        }

        parentScheduleDestination(NavRoutes.DayLogs.route, navController, settingsViewModel) {
            DayLogsScreen(dayLogs = dayLogs,
                alarmPermission = alarmPermission, onBack = { navController.popBackStack() },
                onCompare = { navController.navigate(NavRoutes.WeeklyComparison.route) },
                onEditPlan = { navController.navigate(NavRoutes.Schedule.route) },
                onStartTask = { task ->
                    if (task.taskType == TaskType.LEARNING_GAMES) navController.navigate(NavRoutes.LearningHub.route)
                    else { timerViewModel.startFocusForTask(task); navController.navigate(NavRoutes.Focus.route) }
                })
        }
        parentScheduleDestination(NavRoutes.WeeklyComparison.route, navController, settingsViewModel) {
            com.kidfocus.timer.ui.screens.WeeklyComparisonScreen(dayLogs, onBack = { navController.popBackStack() })
        }

        // ---- AI Chat -------------------------------------------------------------------------
        if (BuildConfig.ENABLE_AI_CHAT) {
            composable(NavRoutes.AiChat.route) {
                AiChatScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPremium = { navController.navigate(NavRoutes.Subscription.route) },
                )
            }
        }

        // ---- Learning ------------------------------------------------------------------------
        composable(NavRoutes.LearningHub.route) {
            LearningHubScreen(
                onBack = { navController.popBackStack() },
                onOpenProgress = { navController.navigate(NavRoutes.LearningProgress.route) },
            )
        }

        composable(NavRoutes.LearningProgress.route) {
            LearningProgressScreen(onBack = { navController.popBackStack() })
        }

        // ---- Task Edit -----------------------------------------------------------------------
        composable(
            route = NavRoutes.TaskEdit.route,
            arguments = listOf(
                navArgument(NavRoutes.ARG_TASK_ID) { type = NavType.LongType },
                navArgument(NavRoutes.ARG_TASK_TYPE) { type = NavType.StringType },
                navArgument(NavRoutes.ARG_HOUR) { type = NavType.IntType; defaultValue = -1 },
                navArgument(NavRoutes.ARG_MINUTE) { type = NavType.IntType; defaultValue = -1 },
            ),
        ) { backStack ->
            val taskId = backStack.arguments?.getLong(NavRoutes.ARG_TASK_ID) ?: 0L
            val taskTypeName = backStack.arguments?.getString(NavRoutes.ARG_TASK_TYPE) ?: TaskType.CUSTOM.name
            val preHour = backStack.arguments?.getInt(NavRoutes.ARG_HOUR) ?: -1
            val preMinute = backStack.arguments?.getInt(NavRoutes.ARG_MINUTE) ?: -1
            val taskType = runCatching { TaskType.valueOf(taskTypeName) }.getOrDefault(TaskType.CUSTOM)
            val tasks by scheduleViewModel.tasks.collectAsState()
            val existing = tasks.find { it.id == taskId }
            val base = existing ?: scheduleViewModel.taskFromType(taskType)
            val task = if (preHour >= 0) base.copy(hour = preHour, minute = preMinute.coerceAtLeast(0)) else base
            TaskEditScreen(
                task = task,
                viewModel = scheduleViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        // ---- Routines -------------------------------------------------------------------------
        composable(NavRoutes.RoutineSettings.route) {
            RoutineSettingsScreen(
                viewModel = routineViewModel,
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(NavRoutes.RoutineEditor.buildRoute(null)) },
                onEdit = { navController.navigate(NavRoutes.RoutineEditor.buildRoute(it)) },
            )
        }

        composable(
            route = NavRoutes.RoutineEditor.route,
            arguments = listOf(
                navArgument(NavRoutes.ARG_ROUTINE_ID) { type = NavType.LongType }
            ),
        ) { backStack ->
            val id = backStack.arguments?.getLong(NavRoutes.ARG_ROUTINE_ID)?.takeIf { it > 0L }
            RoutineEditorScreen(
                routineId = id,
                viewModel = routineViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.RoutineRunner.route) {
            RoutineRunnerScreen(
                viewModel = routineViewModel,
                settingsViewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onStartTimer = { todayRoutine ->
                    todayRoutine.routine.linkedTimerMinutes?.let { minutes ->
                        timerViewModel.startFocusForRoutine(
                            routineId = todayRoutine.routine.id,
                            occurrenceDate = todayRoutine.occurrenceDate,
                            totalSeconds = minutes * 60,
                        )
                        navController.navigate(NavRoutes.Focus.route)
                    }
                },
                onOpenProgress = { navController.navigate(NavRoutes.RoutineProgress.route) },
            )
        }

        composable(NavRoutes.RoutineProgress.route) {
            RoutineProgressScreen(
                viewModel = routineViewModel,
                onBack = { navController.popBackStack() },
            )
        }


        composable(NavRoutes.ChildProfilePicker.route) {
            ChildProfilePickerScreen(
                viewModel = childProfileViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.ChildProfileSettings.route) {
            ChildProfileSettingsScreen(
                viewModel = childProfileViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
    } // end Box
}
