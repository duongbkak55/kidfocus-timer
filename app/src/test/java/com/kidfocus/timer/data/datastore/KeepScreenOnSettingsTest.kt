package com.kidfocus.timer.data.datastore

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kidfocus.timer.domain.model.TimerSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class KeepScreenOnSettingsTest {
    @Test fun defaultsOnAndPersistsParentChoice() = runBlocking {
        assertTrue(TimerSettings().keepScreenOnEnabled)
        val store = SettingsDataStore(ApplicationProvider.getApplicationContext())
        assertTrue(store.settingsFlow.first().keepScreenOnEnabled)
        store.saveSettings(store.settingsFlow.first().copy(keepScreenOnEnabled = false))
        assertFalse(store.settingsFlow.first().keepScreenOnEnabled)
    }
}
