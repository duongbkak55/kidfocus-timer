package com.kidfocus.timer.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for KidFocus Timer session history.
 *
 * Increment [version] and provide a [Migration] whenever the schema changes.
 */
@Database(
    entities = [SessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SessionDatabase : RoomDatabase() {

    /** Provides access to [SessionEntity] persistence operations. */
    abstract fun sessionDao(): SessionDao

    companion object {
        const val DATABASE_NAME = "kidfocus_sessions.db"
    }
}
