package com.kidfocus.timer.data.datastore

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class FocusExtensionRetentionTest {
    @Test fun entriesOlderThanSixtyDaysAreRemovedButRecentOnesRemain() = runBlocking {
        val store = SettingsDataStore(ApplicationProvider.getApplicationContext())
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val oldId = UUID.randomUUID().toString()
        val recentId = UUID.randomUUID().toString()
        store.addFocusExtension(oldId, 5, now - 61 * day)
        store.addFocusExtension(recentId, 10, now - 59 * day)

        store.pruneFocusExtensions(now)

        val reloaded = SettingsDataStore(ApplicationProvider.getApplicationContext())
        val extensions = reloaded.focusExtensions.first()
        assertFalse(extensions.containsKey(oldId))
        assertEquals(10, extensions[recentId])
    }

    @Test fun addingMoreTimeRefreshesRetentionDate() = runBlocking {
        val store = SettingsDataStore(ApplicationProvider.getApplicationContext())
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val id = UUID.randomUUID().toString()
        store.addFocusExtension(id, 5, now - 61 * day)
        store.addFocusExtension(id, 5, now)
        store.pruneFocusExtensions(now)
        assertEquals(10, store.focusExtensions.first()[id])
    }
}
