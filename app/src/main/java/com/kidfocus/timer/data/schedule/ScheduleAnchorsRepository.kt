package com.kidfocus.timer.data.schedule

import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.domain.schedule.ScheduleAnchors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class ScheduleAnchorsRepository @Inject constructor(private val settings: SettingsDataStore) {
    val all: Flow<Map<String, ScheduleAnchors>> = settings.scheduleAnchorsJson.map { rows ->
        rows.mapNotNull { (profile, json) -> runCatching { profile to ScheduleJson.decodeAnchors(json) }.getOrNull() }.toMap()
    }
    fun observe(profileId: String): Flow<ScheduleAnchors> = all.map { it[profileId] ?: ScheduleAnchors() }
    suspend fun get(profileId: String): ScheduleAnchors = observe(profileId).first()
    suspend fun save(profileId: String, anchors: ScheduleAnchors) {
        anchors.validate()
        settings.saveScheduleAnchorsJson(mapOf(profileId to ScheduleJson.encodeAnchors(anchors)))
    }
    suspend fun applyRemote(value: Any?) {
        val rows = value as? Map<*, *> ?: return // Older clients omit the key: keep local data.
        val decoded = rows.entries.mapNotNull { (profile, raw) ->
            val id = profile as? String ?: return@mapNotNull null
            runCatching { id to ScheduleJson.encodeAnchors(ScheduleJson.anchorsFromMap(raw as Map<*, *>)) }.getOrNull()
        }.toMap()
        settings.saveScheduleAnchorsJson(decoded)
    }
}
