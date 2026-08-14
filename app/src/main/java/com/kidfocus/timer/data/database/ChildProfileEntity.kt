package com.kidfocus.timer.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "child_profiles")
data class ChildProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "avatar_emoji")
    val avatarEmoji: String,
    @ColumnInfo(name = "age_band")
    val ageBand: String,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
    val archived: Boolean = false,
) {
    companion object {
        const val DEFAULT_ID = "default"

        fun default() = ChildProfileEntity(
            id = DEFAULT_ID,
            name = "Bé",
            avatarEmoji = "🐣",
            ageBand = "4-5",
            createdAtMillis = 0L,
        )
    }
}
