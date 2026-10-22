package com.kidfocus.timer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.kidfocus.timer.ui.screens.PinEntryScreen
import com.kidfocus.timer.ui.screens.CloudSyncScreen
import com.kidfocus.timer.ui.screens.TaskEditScreen
import com.kidfocus.timer.ui.viewmodel.CloudSyncViewModel
import com.kidfocus.timer.ui.viewmodel.ScheduleViewModel
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel

@Composable
internal fun ParentSessionLifecycle(settingsViewModel: SettingsViewModel) {
    DisposableEffect(settingsViewModel) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(settingsViewModel)
        onDispose { lifecycle.removeObserver(settingsViewModel) }
    }
}

/** Shared by the app graph and the navigation tests, including the real PIN screen. */
internal fun NavGraphBuilder.parentPinEntry(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
) {
    composable(
        route = NavRoutes.PinEntry.route,
        arguments = listOf(navArgument(NavRoutes.ARG_DESTINATION) { type = NavType.StringType }),
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
}

internal fun NavGraphBuilder.parentScheduleDestinations(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    smartScreen: @Composable () -> Unit,
    quickScreen: @Composable () -> Unit,
) {
    parentScheduleDestination(NavRoutes.SmartSchedule.route, navController, settingsViewModel, smartScreen)
    parentScheduleDestination(NavRoutes.QuickSchedule.route, navController, settingsViewModel, quickScreen)
}

internal fun NavGraphBuilder.parentScheduleDestination(
    route: String,
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    screen: @Composable () -> Unit,
) {
    composable(route) {
        ParentProtectedContent(route, navController, settingsViewModel, screen = screen)
    }
}

/** Gate the destination itself, including parameterized editors and restored back stacks. */
@Composable
internal fun ParentProtectedContent(
    destination: String,
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    routePattern: String = destination,
    screen: @Composable () -> Unit,
) {
    val topEntry by navController.currentBackStackEntryAsState()
    val parentUnlocked by settingsViewModel.parentUnlocked.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val current = settings ?: return
    if (parentUnlocked && current.hasPinSet) {
        screen()
    } else {
        LaunchedEffect(destination, current.hasPinSet, topEntry?.id) {
            // An outgoing parent screen must not redirect after Home has locked the session.
            if (topEntry?.destination?.route != routePattern) return@LaunchedEffect
            val currentId = navController.currentBackStackEntry?.destination?.id
            navController.navigate(parentGateRoute(current.hasPinSet, destination)) {
                if (currentId != null) popUpTo(currentId) { inclusive = true }
            }
        }
    }
}

/** Returning to the child area ends the parent session, even from a direct account entry. */
@Composable
internal fun ParentSessionNavigation(navController: NavHostController, settings: SettingsViewModel) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    LaunchedEffect(route) {
        if (route in setOf(NavRoutes.Home.route, NavRoutes.DailySchedule.route,
                NavRoutes.TodayActivity.route, NavRoutes.Focus.route, NavRoutes.BreakTimer.route,
                NavRoutes.LearningHub.route, NavRoutes.ChildQuickLog.route)) settings.lockParentSession()
    }
}

internal fun NavGraphBuilder.parentAccountDestination(
    navController: NavHostController,
    settings: SettingsViewModel,
    account: CloudSyncViewModel,
) {
    parentScheduleDestination(NavRoutes.CloudSync.route, navController, settings) {
        CloudSyncScreen(account, onBack = { navController.popBackStack() })
    }
}

internal fun NavGraphBuilder.parentTaskEditorDestination(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    scheduleViewModel: ScheduleViewModel,
) {
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
        val typeName = backStack.arguments?.getString(NavRoutes.ARG_TASK_TYPE) ?: TaskType.CUSTOM.name
        val hour = backStack.arguments?.getInt(NavRoutes.ARG_HOUR) ?: -1
        val minute = backStack.arguments?.getInt(NavRoutes.ARG_MINUTE) ?: -1
        val type = runCatching { TaskType.valueOf(typeName) }.getOrDefault(TaskType.CUSTOM)
        ParentProtectedContent(NavRoutes.TaskEdit.buildRoute(taskId, type.name, hour, minute),
            navController, settingsViewModel, routePattern = NavRoutes.TaskEdit.route) {
            val tasks by scheduleViewModel.tasks.collectAsState()
            val base = tasks.find { it.id == taskId } ?: scheduleViewModel.taskFromType(type)
            val task = if (hour >= 0) base.copy(hour = hour, minute = minute.coerceAtLeast(0)) else base
            TaskEditScreen(task, scheduleViewModel, onBack = { navController.popBackStack() },
                onTodaySession = {
                    navController.navigate(NavRoutes.TodayActivity.route) {
                        popUpTo(NavRoutes.TaskEdit.route) { inclusive = true }
                    }
                })
        }
    }
}
