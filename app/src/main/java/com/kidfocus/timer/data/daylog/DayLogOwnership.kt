package com.kidfocus.timer.data.daylog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.kidfocus.timer.domain.daylog.DayLogEntry
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Ownership metadata stays outside Room's add-only v6 schema and the compact backup. */
@Singleton
class DayLogOwnership @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("day_log_sync", Context.MODE_PRIVATE)
    @Synchronized fun owner(id: String): String? = preferences.getString("owner:$id", null)
    @Synchronized fun claim(id: String, uid: String) {
        if (owner(id) == null) check(preferences.edit().putString("owner:$id", uid).commit())
    }
    fun visible(id: String, uid: String?): Boolean = owner(id).let { it == null || it == uid }
    fun wasUploaded(entry: DayLogEntry, uid: String): Boolean =
        preferences.getString("uploaded:$uid:${entry.id}", null) == fingerprint(entry)
    fun acknowledge(entry: DayLogEntry, uid: String) {
        preferences.edit().putString("uploaded:$uid:${entry.id}", fingerprint(entry)).apply()
    }
    // Include content so an equal-timestamp tombstone or deterministic tie winner still uploads.
    private fun fingerprint(entry: DayLogEntry) = MessageDigest.getInstance("SHA-256")
        .digest(entry.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
