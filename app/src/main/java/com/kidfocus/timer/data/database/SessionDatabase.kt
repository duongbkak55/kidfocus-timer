package com.kidfocus.timer.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionEntity::class,
        ScheduledTaskEntity::class,
        RoutineEntity::class,
        RoutineCompletionEntity::class,
        LearningAttemptEntity::class,
        ChildProfileEntity::class,
        DayLogEntryEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class SessionDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun routineDao(): RoutineDao
    abstract fun learningAttemptDao(): LearningAttemptDao
    abstract fun dayLogDao(): DayLogDao
    abstract fun childProfileDao(): ChildProfileDao

    companion object {
        const val DATABASE_NAME = "kidfocus_sessions.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createScheduledTasksTable(db)
            }
        }

        /**
         * Adds deadline-based routines while retaining the existing study schedule.
         * The scheduled task table is created defensively because an early local build
         * used database version 2 for routines before the GitHub schedule was merged.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createScheduledTasksTable(db)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `routines` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `deadline_minutes` INTEGER NOT NULL,
                        `repeat_days_mask` INTEGER NOT NULL,
                        `reminder_minutes_before` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `linked_timer_minutes` INTEGER,
                        `created_at_millis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `routine_completions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `routine_id` INTEGER NOT NULL,
                        `occurrence_date` TEXT NOT NULL,
                        `scheduled_deadline_millis` INTEGER NOT NULL,
                        `completed_at_millis` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_completions_routine_id` ON `routine_completions` (`routine_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_routine_completions_routine_id_occurrence_date` ON `routine_completions` (`routine_id`, `occurrence_date`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `learning_attempts` (
                        `id` TEXT NOT NULL,
                        `owner_uid` TEXT,
                        `game_id` TEXT NOT NULL,
                        `age_band` TEXT NOT NULL,
                        `score` INTEGER,
                        `total_questions` INTEGER,
                        `duration_millis` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `created_at_millis` INTEGER NOT NULL,
                        `synced` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_attempts_owner_uid_created_at_millis` ON `learning_attempts` (`owner_uid`, `created_at_millis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_attempts_owner_uid_synced` ON `learning_attempts` (`owner_uid`, `synced`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `child_profiles` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `avatar_emoji` TEXT NOT NULL,
                        `age_band` TEXT NOT NULL,
                        `created_at_millis` INTEGER NOT NULL,
                        `archived` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO child_profiles(id, name, avatar_emoji, age_band, created_at_millis, archived) " +
                        "VALUES('default', 'Bé', '🐣', '4-5', 0, 0)",
                )
                db.execSQL("ALTER TABLE sessions ADD COLUMN child_profile_id TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN childProfileId TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN photoUri TEXT")
                db.execSQL("ALTER TABLE routines ADD COLUMN child_profile_id TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("ALTER TABLE routines ADD COLUMN photo_uri TEXT")
                db.execSQL("ALTER TABLE learning_attempts ADD COLUMN child_profile_id TEXT NOT NULL DEFAULT 'default'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN scheduled_task_id INTEGER")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS day_log_entries (
                        id TEXT NOT NULL PRIMARY KEY, profile_id TEXT NOT NULL, date TEXT NOT NULL,
                        task_id INTEGER, name TEXT NOT NULL, category TEXT NOT NULL,
                        start_minute INTEGER NOT NULL, end_minute INTEGER, source TEXT NOT NULL,
                        created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_day_log_entries_profile_id_date ON day_log_entries(profile_id, date)")
            }
        }

        private fun createScheduledTasksTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `scheduled_tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskType` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `emoji` TEXT NOT NULL,
                    `hour` INTEGER NOT NULL,
                    `minute` INTEGER NOT NULL,
                    `daysOfWeek` TEXT NOT NULL,
                    `focusDurationMinutes` INTEGER NOT NULL,
                    `breakDurationMinutes` INTEGER NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `isCustom` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }
}
