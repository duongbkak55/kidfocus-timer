package com.kidfocus.timer.data.repository

import com.kidfocus.timer.data.database.RoutineCompletionEntity
import com.kidfocus.timer.data.database.RoutineDao
import com.kidfocus.timer.data.database.RoutineEntity
import com.kidfocus.timer.domain.model.RoutineStatus
import com.kidfocus.timer.domain.model.RoutineTime
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
) {
    fun observeAll(): Flow<List<RoutineEntity>> = routineDao.observeAll()

    fun observeForDate(date: LocalDate): Flow<List<RoutineEntity>> =
        routineDao.observeForDay(RoutineTime.dayBit(date.dayOfWeek))

    fun observeCompletions(date: LocalDate): Flow<List<RoutineCompletionEntity>> =
        routineDao.observeCompletions(date.toString())

    fun observeAllCompletions(): Flow<List<RoutineCompletionEntity>> =
        routineDao.observeAllCompletions()

    fun observeCompletionsSince(date: LocalDate): Flow<List<RoutineCompletionEntity>> =
        routineDao.observeCompletionsSince(date.toString())

    fun observeCompletionCount(): Flow<Int> = routineDao.observeCompletionCount()

    fun observeCompletionsSince(profileId: String, date: LocalDate): Flow<List<RoutineCompletionEntity>> =
        routineDao.observeCompletionsSinceForProfile(profileId, date.toString())

    fun observeCompletionCount(profileId: String): Flow<Int> =
        routineDao.observeCompletionCountForProfile(profileId)

    suspend fun getById(id: Long): RoutineEntity? = routineDao.getById(id)

    suspend fun getEnabled(): List<RoutineEntity> = routineDao.getEnabled()

    suspend fun save(routine: RoutineEntity): RoutineEntity {
        validate(routine)
        return if (routine.id == 0L) {
            routine.copy(id = routineDao.insert(routine))
        } else {
            routineDao.update(routine)
            routine
        }
    }

    suspend fun savePreset(routines: List<RoutineEntity>): List<RoutineEntity> {
        routines.forEach(::validate)
        return routineDao.insertPreset(routines)
    }

    suspend fun delete(routine: RoutineEntity) = routineDao.delete(routine)

    suspend fun complete(
        routine: RoutineEntity,
        occurrenceDate: LocalDate,
        completedAtMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        val deadline = RoutineTime.deadlineMillis(occurrenceDate, routine.deadlineMinutes, zoneId)
        routineDao.saveBestCompletion(
            RoutineCompletionEntity(
                routineId = routine.id,
                occurrenceDate = occurrenceDate.toString(),
                scheduledDeadlineMillis = deadline,
                completedAtMillis = completedAtMillis,
                status = if (completedAtMillis <= deadline) RoutineStatus.ON_TIME.name else RoutineStatus.LATE.name,
            )
        )
    }

    private fun validate(routine: RoutineEntity) {
        require(routine.title.isNotBlank()) { "Routine title cannot be blank" }
        require(routine.deadlineMinutes in 0..1439) { "Deadline must be within one day" }
        require(routine.repeatDaysMask in 1..RoutineTime.EVERY_DAY_MASK) { "Select at least one day" }
        require(routine.reminderMinutesBefore in 0..180) { "Reminder must be between 0 and 180 minutes" }
        require(routine.linkedTimerMinutes == null || routine.linkedTimerMinutes in 5..120) {
            "Linked timer must be between 5 and 120 minutes"
        }
    }
}
