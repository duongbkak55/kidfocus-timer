package com.kidfocus.timer.data.daylog

import com.kidfocus.timer.data.database.SessionDatabase

import androidx.room.testing.MigrationTestHelper
import android.app.Application
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.res.AssetManager
import org.robolectric.RuntimeEnvironment
import io.mockk.*
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Migration5To6LocalTest {
    // Load the exported schemas from disk; the SQL database and migration helper are real.
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
    @Test fun addOnlyMigrationKeepsEveryOldTableAndNewColumnIsNullable() {
        val name = "migration-5-6-w5a-local"
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO child_profiles VALUES ('child-old','Old profile','x','l1',1000,0)")
            execSQL("INSERT INTO sessions(duration_seconds,is_focus,timestamp_millis,child_profile_id) VALUES(1500,1,1000,'child-old')")
            execSQL("INSERT INTO scheduled_tasks(taskType,name,emoji,hour,minute,daysOfWeek,focusDurationMinutes,breakDurationMinutes,enabled,isCustom,childProfileId) VALUES('HOMEWORK','Old task','x',18,0,'2',25,5,1,0,'child-old')")
            execSQL("INSERT INTO routines(title,emoji,deadline_minutes,repeat_days_mask,reminder_minutes_before,enabled,created_at_millis,child_profile_id) VALUES('Old routine','x',1080,1,10,1,1000,'child-old')")
            execSQL("INSERT INTO routine_completions(routine_id,occurrence_date,scheduled_deadline_millis,completed_at_millis,status) VALUES(1,'2026-09-28',1000,1000,'ON_TIME')")
            execSQL("INSERT INTO learning_attempts(id,owner_uid,game_id,age_band,duration_millis,completed,created_at_millis,synced,child_profile_id) VALUES('old-attempt',NULL,'count','l1',10000,1,1000,0,'child-old')")
            close()
        }
        helper.runMigrationsAndValidate(name,6,true,SessionDatabase.MIGRATION_5_6).use { db ->
            for (table in listOf("child_profiles","sessions","scheduled_tasks","routines","routine_completions","learning_attempts")) {
                db.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(1,it.getInt(0)) }
            }
            db.query("SELECT duration_seconds,child_profile_id,scheduled_task_id FROM sessions").use { assertTrue(it.moveToFirst()); assertEquals(1500,it.getInt(0)); assertEquals("child-old",it.getString(1)); assertTrue(it.isNull(2)) }
            db.execSQL("INSERT INTO day_log_entries VALUES('d8830123-02f4-4d45-8d3b-7f86d72c9c22','child-old','2026-09-28',1,'Old task','STUDY',1380,30,'MANUAL',1000,1001,0)")
            db.query("SELECT end_minute,deleted FROM day_log_entries").use { assertTrue(it.moveToFirst()); assertEquals(30,it.getInt(0)); assertEquals(0,it.getInt(1)) }
        }
    }
}
