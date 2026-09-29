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
import androidx.navigation.navArgument
import com.kidfocus.timer.ui.screens.PinEntryScreen
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
        val parentUnlocked by settingsViewModel.parentUnlocked.collectAsState()
        val settings by settingsViewModel.settings.collectAsState()
        val current = settings ?: return@composable
        if (parentUnlocked && current.hasPinSet) {
            screen()
        } else {
            LaunchedEffect(Unit) {
                val gate = parentGateRoute(current.hasPinSet, route)
                navController.navigate(gate) { popUpTo(route) { inclusive = true } }
            }
        }
    }
}
