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
class Migration7To8LocalTest {
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

    @Test fun v7RowsSurviveAndSyncRecordTombstonesHaveACompoundKey() {
        val name = "sync-migration-7-8"
        helper.createDatabase(name, 7).apply {
            execSQL("INSERT INTO sessions(id,duration_seconds,is_focus,timestamp_millis,child_profile_id,completed) " +
                "VALUES(42,900,1,1000,'default',1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 8, true, SessionDatabase.MIGRATION_7_8).use { db ->
            db.query("SELECT id,duration_seconds FROM sessions WHERE id=42").use {
                assertTrue(it.moveToFirst())
                assertEquals(42L, it.getLong(0))
                assertEquals(900, it.getInt(1))
            }
            db.execSQL("INSERT INTO sync_records(ownerUid,collection,docId,payloadJson,fingerprint," +
                "updatedAtMillis,deviceId,deleted,dirty) VALUES('u','tasks','42','{}','x',99,'phone',1,1)")
            db.query("SELECT deleted,dirty FROM sync_records WHERE ownerUid='u' AND collection='tasks' AND docId='42'").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
                assertEquals(1, it.getInt(1))
            }
        }
    }
}
