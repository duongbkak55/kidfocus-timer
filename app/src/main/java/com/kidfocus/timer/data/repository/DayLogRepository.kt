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
        addBatch(entries, DayLogSource.AI)
    }
    suspend fun addChildBatch(entries: List<DayLogEntry>) {
        addBatch(entries, DayLogSource.CHILD)
    }
    private suspend fun addBatch(entries: List<DayLogEntry>, source: DayLogSource) {
        require(entries.isNotEmpty())
        entries.forEach { it.validate(); require(it.profileId == entries.first().profileId && it.source == source) }
        val uid = account.account.value.userId
        entries.forEach { if (uid != null) ownership.claim(it.id, uid) }
        dao.addGeneratedBatch(entries.map(DayLogEntryEntity::fromEntry))
    }
    suspend fun undoAiBatch(expected: List<DayLogEntry>) {
        undoBatch(expected, DayLogSource.AI)
    }
    suspend fun undoChildBatch(expected: List<DayLogEntry>) {
        undoBatch(expected, DayLogSource.CHILD)
    }
    private suspend fun undoBatch(expected: List<DayLogEntry>, source: DayLogSource) {
        check(expected.isNotEmpty() && expected.all { it.source == source &&
            ownership.visible(it.id, account.account.value.userId) }) { "STALE" }
        dao.undoGeneratedBatch(expected.map(DayLogEntryEntity::fromEntry))
    }
    suspend fun edit(entry: DayLogEntry) {
        entry.validate()
        if (!ownership.visible(entry.id, account.account.value.userId)) return
        dao.edit(entry.id) { old ->
            require(old.profileId == entry.profileId && !old.deleted)
            DayLogEntryEntity.fromEntry(entry.copy(createdAt = old.createdAt,
                source = if (old.source == DayLogSource.CHILD.name) DayLogSource.CHILD else DayLogSource.MANUAL))
        }
    }
    suspend fun delete(id: String, profileId: String) {
        if (!ownership.visible(id, account.account.value.userId)) return
        dao.edit(id) { old ->
            require(old.profileId == profileId)
            old.copy(deleted = true, updatedAt = System.currentTimeMillis())
        }
    }
    suspend fun finishTimer(id: String, endMinute: Int, atMillis: Long, completed: Boolean = false) {
        dao.edit(id) { old ->
            if (old.deleted || old.source != DayLogSource.TIMER.name || old.endMinute != null) old
            else old.copy(endMinute = endMinute, source = if (completed) DayLogSource.TIMER_DONE.name else old.source,
                updatedAt = atMillis)
        }
    }
}
