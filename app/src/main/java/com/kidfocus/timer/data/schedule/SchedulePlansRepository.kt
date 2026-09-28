package com.kidfocus.timer.data.schedule

import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.domain.schedule.SchedulePlan
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SchedulePlansRepository @Inject constructor(private val settings: SettingsDataStore) {
    // Explicit nulls are cancellation tombstones; absent remote keys retain local plans.
    val all = settings.schedulePlansJson.map { rows -> rows.mapNotNull { (id, raw) ->
        runCatching { id to if (raw == "null") null else SchedulePlanJson.decode(raw) }.getOrNull()
    }.toMap() }
    fun observe(profileId: String) = all.map { it[profileId] }
    suspend fun get(profileId: String) = observe(profileId).first()
    suspend fun save(profileId: String, plan: SchedulePlan?) {
        plan?.validate()
        settings.saveSchedulePlansJson(mapOf(profileId to (plan?.let(SchedulePlanJson::encode) ?: "null")))
    }
    suspend fun applyRemote(value: Any?) {
        val rows = value as? Map<*, *> ?: return
        val decoded = rows.entries.mapNotNull { (profile, raw) ->
            val id = profile as? String ?: return@mapNotNull null
            runCatching { id to (if (raw == null) "null" else SchedulePlanJson.encode(SchedulePlanJson.fromMap(raw as Map<*, *>))) }.getOrNull()
        }.toMap()
        settings.saveSchedulePlansJson(decoded)
    }
}
