package com.kidfocus.timer.data.repository

import com.kidfocus.timer.data.database.DayLogDao
import com.kidfocus.timer.data.database.DayLogEntryEntity
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.kidfocus.timer.data.daylog.DayLogOwnership
import com.kidfocus.timer.domain.daylog.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayLogRepository @Inject constructor(
    private val dao: DayLogDao,
    private val profiles: ChildProfileRepository,
    private val account: FirebaseAccountRepository,
    private val ownership: DayLogOwnership,
) {
    fun observe(profileId: String): Flow<List<DayLogEntry>> = combine(dao.observeProfile(profileId), account.account) { rows, user ->
        rows.filter { ownership.visible(it.id, user.userId) }.map { it.toEntry() }
    }
    suspend fun activeProfileId(): String? = profiles.currentProfileIdOrNull()
    suspend fun get(id: String): DayLogEntry? = dao.get(id)?.toEntry()
    suspend fun add(entry: DayLogEntry) {
        entry.validate()
        account.account.value.userId?.let { ownership.claim(entry.id, it) }
        dao.merge(DayLogEntryEntity.fromEntry(entry))
    }
    suspend fun addAiBatch(entries: List<DayLogEntry>) {
        entries.forEach { it.validate(); require(it.profileId == entries.first().profileId && it.source == DayLogSource.AI) }
        val uid = account.account.value.userId
        entries.forEach { if (uid != null) ownership.claim(it.id, uid) }
        dao.addAiBatch(entries.map(DayLogEntryEntity::fromEntry))
    }
    suspend fun undoAiBatch(expected: List<DayLogEntry>) {
        check(expected.all { ownership.visible(it.id, account.account.value.userId) }) { "STALE" }
        dao.undoAiBatch(expected.map(DayLogEntryEntity::fromEntry))
    }
    suspend fun edit(entry: DayLogEntry) {
        entry.validate()
        if (!ownership.visible(entry.id, account.account.value.userId)) return
        dao.edit(entry.id) { old ->
            require(old.profileId == entry.profileId && !old.deleted)
            DayLogEntryEntity.fromEntry(entry.copy(createdAt = old.createdAt, source = DayLogSource.MANUAL))
        }
    }
    suspend fun delete(id: String, profileId: String) {
        if (!ownership.visible(id, account.account.value.userId)) return
        dao.edit(id) { old ->
            require(old.profileId == profileId)
            old.copy(deleted = true, updatedAt = System.currentTimeMillis())
        }
    }
    suspend fun finishTimer(id: String, endMinute: Int, atMillis: Long) {
        dao.edit(id) { old ->
            if (old.deleted || old.source != DayLogSource.TIMER.name || old.endMinute != null) old
            else old.copy(endMinute = endMinute, updatedAt = atMillis)
        }
    }
}
