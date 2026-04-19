package com.kidfocus.timer.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SessionEntity::class, ScheduledTaskEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SessionDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao

    companion object {
        const val DATABASE_NAME = "kidfocus_sessions.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS scheduled_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        taskType TEXT NOT NULL,
                        name TEXT NOT NULL,
                        emoji TEXT NOT NULL,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        daysOfWeek TEXT NOT NULL,
                        focusDurationMinutes INTEGER NOT NULL,
                        breakDurationMinutes INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        isCustom INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
