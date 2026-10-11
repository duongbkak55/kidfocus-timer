package com.kidfocus.timer.data.database

import android.app.Application
import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.res.AssetManager
import androidx.room.testing.MigrationTestHelper
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Migration6To7LocalTest {
    private val schemaAssets = mockk<AssetManager>().also { manager ->
        every { manager.open(any<String>()) } answers { File("schemas", firstArg<String>()).inputStream() }
    }
    private val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getAssets(): AssetManager = schemaAssets
    }
    private val instrumentation = mockk<Instrumentation>().also {
        every { it.context } returns context
        every { it.targetContext } returns context
    }
    @get:Rule val helper = MigrationTestHelper(instrumentation, SessionDatabase::class.java)

    @Test fun oldV6SessionSurvivesWithNullableTaskAndCompletedDefault() {
        val name = "history-migration-6-7"
        helper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO sessions(duration_seconds,is_focus,timestamp_millis,child_profile_id,scheduled_task_id) VALUES(900,1,1000,'be-na',42)")
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true, SessionDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT duration_seconds,scheduled_task_id,task_id,task_title,completed FROM sessions").use {
                assertTrue(it.moveToFirst())
                assertEquals(900, it.getInt(0))
                assertEquals(42L, it.getLong(1))
                assertTrue(it.isNull(2))
                assertTrue(it.isNull(3))
                assertEquals(1, it.getInt(4))
            }
        }
    }

    @Test fun existingV5FixtureMigratesThroughV6WithoutDataLoss() {
        val name = "history-migration-5-7"
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO sessions(duration_seconds,is_focus,timestamp_millis,child_profile_id) VALUES(1500,1,1000,'be-na')")
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true,
            SessionDatabase.MIGRATION_5_6, SessionDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT duration_seconds,completed FROM sessions").use {
                assertTrue(it.moveToFirst())
                assertEquals(1500, it.getInt(0))
                assertEquals(1, it.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM day_log_entries").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
    }
}
