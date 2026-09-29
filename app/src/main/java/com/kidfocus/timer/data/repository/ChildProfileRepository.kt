package com.kidfocus.timer.data.repository

import com.kidfocus.timer.data.database.ChildProfileDao
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.datastore.SettingsDataStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class ChildProfileRepository @Inject constructor(
    private val dao: ChildProfileDao,
    private val settingsDataStore: SettingsDataStore,
) {
    val profiles: Flow<List<ChildProfileEntity>> = dao.observeActive()

    private val storedActiveProfileId: Flow<String> = settingsDataStore.settingsFlow
        .map { it.activeChildProfileId }
        .distinctUntilChanged()

    val activeProfileId: Flow<String> = combine(profiles, storedActiveProfileId) { rows, storedId ->
        storedId.takeIf { id -> rows.any { it.id == id } }
            ?: rows.firstOrNull()?.id
            ?: ChildProfileEntity.DEFAULT_ID
    }.distinctUntilChanged()

    val activeProfile: Flow<ChildProfileEntity> = combine(profiles, activeProfileId) { rows, activeId ->
        rows.firstOrNull { it.id == activeId }
            ?: rows.firstOrNull()
            ?: ChildProfileEntity.default()
    }.distinctUntilChanged()

    suspend fun ensureDefaultProfile() {
        if (dao.getById(ChildProfileEntity.DEFAULT_ID) == null) {
            dao.upsert(ChildProfileEntity.default())
        }
        val rows = profiles.first()
        val activeId = storedActiveProfileId.first()
        if (rows.none { it.id == activeId }) {
            settingsDataStore.saveActiveChildProfileId(rows.firstOrNull()?.id ?: ChildProfileEntity.DEFAULT_ID)
        }
    }

    suspend fun currentProfileIdOrNull(): String? {
        val rows = profiles.first()
        val selected = storedActiveProfileId.first()
        return rows.firstOrNull { it.id == selected }?.id ?: rows.firstOrNull()?.id
    }

    suspend fun currentProfileId(): String = activeProfile.first().id

    suspend fun select(profileId: String) {
        val profile = dao.getById(profileId) ?: return
        if (!profile.archived) settingsDataStore.saveActiveChildProfileId(profileId)
    }

    suspend fun save(
        id: String? = null,
        name: String,
        avatarEmoji: String,
        ageBand: String,
    ): ChildProfileEntity {
        require(name.isNotBlank()) { "Profile name cannot be blank" }
        require(ageBand in VALID_AGE_BANDS) { "Unsupported age band" }
        val existing = id?.let { dao.getById(it) }
        val profile = ChildProfileEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim().take(40),
            avatarEmoji = avatarEmoji.ifBlank { "🐣" }.take(8),
            ageBand = ageBand,
            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
            archived = false,
        )
        dao.upsert(profile)
        return profile
    }

    suspend fun updateActiveAgeBand(ageBand: String) {
        if (ageBand !in VALID_AGE_BANDS) return
        val current = activeProfile.first()
        dao.upsert(current.copy(ageBand = ageBand))
    }

    suspend fun archive(profile: ChildProfileEntity): Boolean {
        val activeRows = profiles.first()
        if (activeRows.size <= 1) return false
        dao.upsert(profile.copy(archived = true))
        if (storedActiveProfileId.first() == profile.id) {
            activeRows.firstOrNull { it.id != profile.id }?.let { select(it.id) }
        }
        return true
    }

    private companion object {
        val VALID_AGE_BANDS = setOf("2-3", "4-5", "l1", "l2", "l3")
    }
}
