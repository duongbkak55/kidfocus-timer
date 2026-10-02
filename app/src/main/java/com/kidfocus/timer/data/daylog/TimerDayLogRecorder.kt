package com.kidfocus.timer.data.daylog

import com.kidfocus.timer.data.repository.DayLogRepository
import com.kidfocus.timer.data.repository.ScheduledTaskRepository
import com.kidfocus.timer.domain.daylog.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class TimerLogContext(val profileId: String?, val taskId: Long?, val logId: String? = null)

/** Owned by the existing timer service, so notification Stop and background completion also close logs. */
@Singleton
class TimerDayLogRecorder @Inject constructor(private val logs: DayLogRepository, private val tasks: ScheduledTaskRepository) {
    private val mutex = Mutex()
    private var activeId: String? = null
    suspend fun start(taskId: Long?, freeName: String, atMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(), record: Boolean = true): TimerLogContext = mutex.withLock {
        finishLocked(atMillis, zone)
        val profileId = logs.activeProfileId() ?: return@withLock TimerLogContext(null, null)
        if (!record) return@withLock TimerLogContext(profileId, null)
        val task = taskId?.let { id -> tasks.allTasks.first().firstOrNull { it.id == id && it.childProfileId == profileId } }
        val now = Instant.ofEpochMilli(atMillis).atZone(zone)
        val entry = DayLogEntry(profileId = profileId, date = now.toLocalDate(), taskId = task?.id,
            name = task?.name ?: freeName, category = task?.taskType?.category?.name?.let(DayLogCategory::valueOf) ?: DayLogCategory.STUDY,
            startMinute = now.hour * 60 + now.minute, source = DayLogSource.TIMER, createdAt = atMillis)
        logs.add(entry)
        activeId = entry.id
        TimerLogContext(profileId, task?.id, entry.id)
    }
    suspend fun finish(atMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) = mutex.withLock {
        finishLocked(atMillis, zone)
    }
    private suspend fun finishLocked(atMillis: Long, zone: ZoneId) {
        val id = activeId ?: return
        val now = Instant.ofEpochMilli(atMillis).atZone(zone)
        logs.finishTimer(id, now.hour * 60 + now.minute, atMillis)
        activeId = null
    }
}
