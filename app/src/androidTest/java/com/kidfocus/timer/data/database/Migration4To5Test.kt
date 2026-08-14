package com.kidfocus.timer.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SessionDatabase::class.java,
    )

    @Test
    fun migration4To5_keepsExistingRowsAndAssignsDefaultProfile() {
        helper.createDatabase(TEST_DATABASE, 4).apply {
            execSQL(
                "INSERT INTO sessions(duration_seconds, is_focus, timestamp_millis) " +
                    "VALUES(1500, 1, 1000)",
            )
            execSQL(
                "INSERT INTO scheduled_tasks(taskType, name, emoji, hour, minute, daysOfWeek, " +
                    "focusDurationMinutes, breakDurationMinutes, enabled, isCustom) " +
                    "VALUES('HOMEWORK', 'Học bài', '📚', 19, 0, '2,3,4,5,6', 25, 5, 1, 0)",
            )
            execSQL(
                "INSERT INTO routines(title, emoji, deadline_minutes, repeat_days_mask, " +
                    "reminder_minutes_before, enabled, linked_timer_minutes, created_at_millis) " +
                    "VALUES('Đi ngủ', '🌙', 1320, 127, 10, 1, NULL, 1000)",
            )
            execSQL(
                "INSERT INTO learning_attempts(id, owner_uid, game_id, age_band, score, " +
                    "total_questions, duration_millis, completed, created_at_millis, synced) " +
                    "VALUES('attempt-1', NULL, 'count', '4-5', 4, 5, 10000, 1, 1000, 0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            SessionDatabase.MIGRATION_4_5,
        ).use { database ->
            database.query("SELECT id, name FROM child_profiles").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("default", cursor.getString(0))
                assertEquals("Bé", cursor.getString(1))
            }
            listOf(
                "sessions" to "child_profile_id",
                "scheduled_tasks" to "childProfileId",
                "routines" to "child_profile_id",
                "learning_attempts" to "child_profile_id",
            ).forEach { (table, column) ->
                database.query("SELECT $column FROM $table").use { cursor ->
                    check(cursor.moveToFirst())
                    assertEquals("default", cursor.getString(0))
                }
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-4-5-test"
    }
}
