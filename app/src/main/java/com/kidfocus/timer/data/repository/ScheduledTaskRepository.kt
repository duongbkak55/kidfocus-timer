package com.kidfocus.timer.data.repository

import com.kidfocus.timer.data.database.ScheduledTaskDao
import com.kidfocus.timer.data.database.ScheduledTaskEntity
import com.kidfocus.timer.domain.model.ScheduledTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduledTaskRepository @Inject constructor(
    private val dao: ScheduledTaskDao,
) {
    val allTasks: Flow<List<ScheduledTask>> = dao.getAllFlow().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getEnabledTasks(): List<ScheduledTask> =
        dao.getAllEnabled().map { it.toDomain() }

    suspend fun save(task: ScheduledTask): Long =
        dao.insert(ScheduledTaskEntity.fromDomain(task))

    suspend fun update(task: ScheduledTask) =
        dao.update(ScheduledTaskEntity.fromDomain(task))

    suspend fun delete(task: ScheduledTask) =
        dao.deleteById(task.id)
}
