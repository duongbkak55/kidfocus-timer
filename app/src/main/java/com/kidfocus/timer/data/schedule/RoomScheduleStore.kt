package com.kidfocus.timer.data.schedule

import androidx.room.withTransaction
import com.kidfocus.timer.alarm.AlarmScheduler
import com.kidfocus.timer.data.database.ScheduledTaskDao
import com.kidfocus.timer.data.database.ScheduledTaskEntity
import com.kidfocus.timer.data.database.SessionDatabase
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.schedule.ScheduleSnapshot
import com.kidfocus.timer.domain.schedule.ScheduleState
import com.kidfocus.timer.domain.schedule.ScheduleStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Singleton
class RoomScheduleStore @Inject constructor(
    private val database: SessionDatabase,
    private val dao: ScheduledTaskDao,
    private val anchors: ScheduleAnchorsRepository,
    private val settings: SettingsDataStore,
    private val alarms: AlarmScheduler,
) : ScheduleStore {
    override suspend fun read(profileId: String) = ScheduleState(
        dao.getAllForSync().filter { it.childProfileId == profileId }.map { it.toDomain() }.sortedBy { it.id }, anchors.get(profileId))
    override suspend fun snapshot(profileId: String): ScheduleSnapshot? = settings.getScheduleSnapshotJson(profileId)?.let {
        runCatching { ScheduleJson.decodeSnapshot(it) }.getOrNull()
    }
    override suspend fun saveSnapshot(profileId: String, snapshot: ScheduleSnapshot?) = settings.saveScheduleSnapshotJson(
        profileId, snapshot?.let(ScheduleJson::encodeSnapshot))

    override suspend fun replace(profileId: String, expected: ScheduleState, state: ScheduleState) {
        var anchorsWritten = false
        try {
            database.withTransaction {
                check(read(profileId) == expected) { "Schedule changed" }
                val removed = expected.tasks.map { it.id }.toSet() - state.tasks.map { it.id }.toSet()
                removed.forEach { dao.deleteById(it) }
                dao.insertAll(state.tasks.map(ScheduledTaskEntity::fromDomain))
                anchorsWritten = true
                anchors.save(profileId, state.anchors)
            }
        } catch (error: Exception) {
            // Room rolls back SQL. DataStore is separate: compensate even when cancelled.
            if (anchorsWritten) withContext(NonCancellable) { anchors.save(profileId, expected.anchors) }
            throw error
        }
    }
    override fun reschedule(before: List<ScheduledTask>, after: List<ScheduledTask>) {
        val oldById = before.associateBy { it.id }
        val newById = after.associateBy { it.id }
        before.filter { newById[it.id] != it }.forEach(alarms::cancelTask)
        alarms.scheduleAll(after.filter { oldById[it.id] != it })
    }
}
