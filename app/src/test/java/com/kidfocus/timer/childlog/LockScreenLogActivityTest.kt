package com.kidfocus.timer.childlog

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LockScreenLogActivityTest {
    @Test fun activityIsPrivateEphemeralAndSeparateFromMainNavigationTask() {
        val app = RuntimeEnvironment.getApplication()
        val packageManager = app.packageManager
        val lock = packageManager.getActivityInfo(ComponentName(app, LockScreenLogActivity::class.java),
            PackageManager.GET_META_DATA)
        val main = packageManager.getActivityInfo(ComponentName(app, com.kidfocus.timer.MainActivity::class.java),
            PackageManager.GET_META_DATA)
        assertFalse(lock.exported)
        assertTrue(lock.flags and ActivityInfo.FLAG_NO_HISTORY != 0)
        assertTrue(lock.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
        assertEquals("${app.packageName}.childlog", lock.taskAffinity)
        assertFalse(lock.taskAffinity == main.taskAffinity)
    }
}
