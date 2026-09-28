package com.kidfocus.timer.ui.navigation

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import com.kidfocus.timer.R
import com.kidfocus.timer.data.cloud.CloudAccount
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.remote.AiConfig
import com.kidfocus.timer.data.repository.SettingsRepository
import com.kidfocus.timer.domain.model.TimerSettings
import com.kidfocus.timer.domain.schedule.ScheduleAnchors
import com.kidfocus.timer.domain.schedule.ScheduleState
import com.kidfocus.timer.domain.usecase.GetTimerSettingsUseCase
import com.kidfocus.timer.domain.usecase.SaveTimerSettingsUseCase
import com.kidfocus.timer.ui.screens.OnboardingScreen
import com.kidfocus.timer.ui.screens.QuickScheduleScreen
import com.kidfocus.timer.ui.screens.SmartScheduleScreen
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = ParentNavigationTestApplication::class, qualifiers = "en-w411dp-h891dp")
class ParentScheduleNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: SettingsViewModel
    private lateinit var nav: TestNavHostController
    private val profile = ChildProfileEntity.default()
    private val schedule = ScheduleState(emptyList(), ScheduleAnchors())

    @Before fun setup() {
        val repository = mockk<SettingsRepository>()
        every { repository.settingsFlow } returns MutableStateFlow(TimerSettings(pinHash = "hash", onboardingCompleted = true))
        every { repository.verifyPin(any(), "hash") } answers { firstArg<String>() == "2468" }
        vm = SettingsViewModel(GetTimerSettingsUseCase(repository), SaveTimerSettingsUseCase(repository), repository)
    }

    @After fun teardown() { vm.viewModelScope.cancel() }

    private fun graph(destination: String) {
        val access = mockk<ScheduleAccessViewModel>(relaxed = true)
        every { access.config } returns MutableStateFlow(AiConfig())
        every { access.account } returns MutableStateFlow(CloudAccount(configured = false))
        every { access.signingIn } returns MutableStateFlow(false)
        every { access.signInFailed } returns MutableStateFlow(false)
        val smart = mockk<SmartScheduleViewModel>(relaxed = true)
        every { smart.state } returns MutableStateFlow(SmartScheduleUiState(profile, schedule, emptyList(), false))
        every { smart.busy } returns MutableStateFlow(false)
        every { smart.events } returns MutableSharedFlow()
        every { smart.advice } returns MutableStateFlow(AdviceUiState())
        val quick = mockk<QuickScheduleViewModel>(relaxed = true)
        every { quick.state } returns MutableStateFlow(QuickScheduleState())
        every { quick.profile } returns MutableStateFlow(profile)
        every { quick.current } returns MutableStateFlow(schedule)
        every { quick.events } returns MutableSharedFlow()
        compose.setContent {
            ParentSessionLifecycle(vm)
            val settings by vm.settings.collectAsState()
            if (settings != null) {
                val context = LocalContext.current
                nav = androidx.compose.runtime.remember {
                    TestNavHostController(context).apply {
                        navigatorProvider.addNavigator(ComposeNavigator())
                    }
                }
                KidFocusTheme {
                    NavHost(nav, startDestination = NavRoutes.Home.route) {
                        composable(NavRoutes.Home.route) { Text("Home") }
                        composable(NavRoutes.PinSetup.route) { Text("PIN setup") }
                        parentPinEntry(nav, vm)
                        parentScheduleDestinations(nav, vm,
                            smartScreen = { SmartScheduleScreen({}, access = access, viewModel = smart) },
                            quickScreen = { QuickScheduleScreen({}, access, quick) })
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { nav.navigate(destination) }
        compose.waitForIdle()
        compose.onNodeWithText(compose.activity.getString(R.string.pin_verify_title)).assertIsDisplayed()
    }

    private fun enterPin(pin: String) {
        pin.forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    private fun assertDestination(route: String, title: Int) {
        compose.onNodeWithText(compose.activity.getString(title)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.pin_verify_title)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(route, nav.currentDestination?.route)
            assertFalse(vm.pinVerified.value)
            assertTrue(vm.parentUnlocked.value)
        }
    }

    @Test fun `correct PIN opens the real smart screen without returning to PIN`() {
        graph(NavRoutes.SmartSchedule.route); enterPin("2468")
        assertDestination(NavRoutes.SmartSchedule.route, R.string.smart_title)
    }

    @Test fun `correct PIN opens the real quick entry screen without returning to PIN`() {
        graph(NavRoutes.QuickSchedule.route); enterPin("2468")
        assertDestination(NavRoutes.QuickSchedule.route, R.string.quick_title)
    }

    @Test fun `incorrect PIN stays on PIN instead of opening smart schedule`() {
        graph(NavRoutes.SmartSchedule.route); enterPin("0000")
        compose.onNodeWithText("Incorrect PIN").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(NavRoutes.PinEntry.route, nav.currentDestination?.route)
            assertFalse(vm.parentUnlocked.value)
        }
    }

    @Test fun `incorrect PIN stays on PIN instead of opening quick entry`() {
        graph(NavRoutes.QuickSchedule.route); enterPin("0000")
        compose.onNodeWithText("Incorrect PIN").assertIsDisplayed()
        compose.runOnIdle { assertEquals(NavRoutes.PinEntry.route, nav.currentDestination?.route) }
    }

    private fun backgroundAndReenter(destination: String, title: Int) {
        graph(destination); enterPin("2468"); assertDestination(destination, title)
        compose.runOnIdle { assertEquals(Lifecycle.State.RESUMED, ProcessLifecycleOwner.get().lifecycle.currentState) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(Lifecycle.State.CREATED, ProcessLifecycleOwner.get().lifecycle.currentState)
        assertFalse(vm.parentUnlocked.value)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText(compose.activity.getString(R.string.pin_verify_title)).assertIsDisplayed()
        compose.runOnIdle { assertFalse(vm.parentUnlocked.value) }
        enterPin("2468"); assertDestination(destination, title)
    }

    @Test fun `process ON_STOP requires PIN again for smart schedule`() {
        backgroundAndReenter(NavRoutes.SmartSchedule.route, R.string.smart_title)
    }

    @Test fun `process ON_STOP requires PIN again for quick entry`() {
        backgroundAndReenter(NavRoutes.QuickSchedule.route, R.string.quick_title)
    }

    @Test @Config(qualifiers = "vi-w411dp-h891dp") fun `Vietnamese PIN title and error use locale resources`() {
        graph(NavRoutes.SmartSchedule.route); enterPin("0000")
        compose.onNodeWithText("Nhập PIN phụ huynh").assertIsDisplayed()
        compose.onNodeWithText("PIN không đúng").assertIsDisplayed()
    }

    private fun onboarding(title: String, next: String) {
        compose.setContent { KidFocusTheme { OnboardingScreen {} } }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(next).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.onboarding_pomodoro_title)).assertIsDisplayed()
    }

    @Test fun `English onboarding uses existing localized strings`() {
        onboarding("Welcome to KidFocus!", "Next")
    }

    @Test @Config(qualifiers = "vi-w411dp-h891dp") fun `Vietnamese onboarding uses existing localized strings`() {
        onboarding("Chào mừng đến với KidFocus!", "Tiếp theo")
    }
}

/**
 * Boot the real manifest Startup provider before the test Activity is created.
 * Robolectric keeps AndroidX singletons across fresh Applications, unlike a new app process.
 * Reset only that test infrastructure; ActivityScenario drives the actual ON_STOP event.
 */
class ParentNavigationTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // AndroidX process singletons otherwise survive between Robolectric applications.
        ReflectionHelpers.setStaticField(androidx.startup.AppInitializer::class.java, "sInstance", null)
        val owner = ProcessLifecycleOwner.get()
        ReflectionHelpers.setField(owner, "registry", LifecycleRegistry(owner))
        ReflectionHelpers.setField(owner, "startedCounter", 0)
        ReflectionHelpers.setField(owner, "resumedCounter", 0)
        ReflectionHelpers.setField(owner, "pauseSent", true)
        ReflectionHelpers.setField(owner, "stopSent", true)
        ReflectionHelpers.getStaticField<AtomicBoolean>(
            Class.forName("androidx.lifecycle.LifecycleDispatcher"), "initialized",
        ).set(false)
        val providerInfo = packageManager.getProviderInfo(
            ComponentName(this, androidx.startup.InitializationProvider::class.java),
            PackageManager.GET_META_DATA,
        )
        androidx.startup.InitializationProvider().attachInfo(this, providerInfo)
    }
}
