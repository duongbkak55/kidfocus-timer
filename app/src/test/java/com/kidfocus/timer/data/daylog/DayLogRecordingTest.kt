package com.kidfocus.timer.data.daylog

import android.app.Application
import androidx.room.Room
import com.kidfocus.timer.data.cloud.*
import com.kidfocus.timer.data.database.*
import com.kidfocus.timer.data.repository.*
import com.kidfocus.timer.domain.daylog.*
import com.kidfocus.timer.domain.model.*
import com.kidfocus.timer.domain.usecase.RecordSessionUseCase
import io.mockk.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DayLogRecordingTest {
    private lateinit var db: SessionDatabase
    private lateinit var logs: DayLogRepository
    private lateinit var recorder: TimerDayLogRecorder
    private lateinit var profiles: ChildProfileRepository
    private var active: String? = "default"
    private val account = mockk<FirebaseAccountRepository>()
    private val state = MutableStateFlow(CloudAccount(false))
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun millis(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("day_log_sync",0).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(app,SessionDatabase::class.java).allowMainThreadQueries().build()
        profiles = mockk()
        coEvery { profiles.currentProfileIdOrNull() } coAnswers { active }
        every { account.account } returns state
        logs = DayLogRepository(db.dayLogDao(),profiles,account,DayLogOwnership(app))
        recorder = TimerDayLogRecorder(logs,ScheduledTaskRepository(db.scheduledTaskDao()))
    }
    @After fun close() { db.close() }
    @Test fun scheduledTimerWritesStartThenEndAcrossMidnightAndKeepsItsStartingProfile() = runTest {
        val task = ScheduledTask(id=42, taskType=TaskType.HOMEWORK,name="Homework",emoji="x",hour=23,minute=55,daysOfWeek=setOf(2),focusDurationMinutes=25,breakDurationMinutes=5)
        db.scheduledTaskDao().insert(ScheduledTaskEntity.fromDomain(task))
        val context = recorder.start(42,"Free timer",millis("2026-09-28T23:55:00"),zone)
        assertEquals(42L,context.taskId)
        val initial = db.dayLogDao().getAll().single().toEntry()
        assertEquals(LocalDate.parse("2026-09-28"),initial.date); assertNull(initial.endMinute)
        active = "other"
        recorder.finish(millis("2026-09-29T00:05:00"),zone)
        val ended = db.dayLogDao().getAll().single().toEntry()
        assertEquals("default",ended.profileId); assertEquals(5,ended.endMinute); assertEquals(10,ended.durationMinutes)
        assertEquals(DayLogSource.TIMER,ended.source)
        recorder.finish(millis("2026-09-29T00:10:00"),zone)
        assertEquals(ended,db.dayLogDao().getAll().single().toEntry())
    }
    @Test fun freeTimersHaveNoTaskLinkAndNoProfileCreatesNoEntry() = runTest {
        active = null
        assertNull(recorder.start(null,"Free timer",1000,zone).profileId)
        assertTrue(db.dayLogDao().getAll().isEmpty())
        active = "default"
        recorder.start(null,"Free timer",2000,zone)
        assertNull(db.dayLogDao().getAll().single().taskId)
        recorder.finish(3000,zone)
        recorder.start(null,"Break",4000,zone,record=false)
        assertEquals(1,db.dayLogDao().getAll().size)
    }
    @Test fun startingAnotherTimerClosesTheOldOneAndManualEditsAndDeletesStayAuthoritative() = runTest {
        recorder.start(null,"First",1000,zone)
        recorder.start(null,"Second",61000,zone)
        assertNotNull(db.dayLogDao().getAll().first { it.name == "First" }.endMinute)
        val second = db.dayLogDao().getAll().first { it.name == "Second" }.toEntry()
        logs.edit(second.copy(endMinute=600,updatedAt=62000))
        recorder.finish(121000,zone)
        assertEquals(600,logs.get(second.id)!!.endMinute)
        recorder.start(null,"Third",122000,zone)
        val third=db.dayLogDao().getAll().first { it.name == "Third" }.toEntry()
        logs.delete(third.id,"default")
        recorder.finish(123000,zone)
        assertTrue(logs.get(third.id)!!.deleted)
    }
    @Test fun routineCompletionRecordsTheActualTickOnceAndNeverRecreatesDeletedEntries() = runTest {
        db.childProfileDao().upsert(ChildProfileEntity.default())
        val repo=RoutineRepository(db.routineDao(),logs,db.childProfileDao())
        val routine=repo.save(RoutineEntity(title="Brush teeth",emoji="x",deadlineMinutes=1200,repeatDaysMask=127))
        val date=LocalDate.parse("2026-09-28")
        val tick=millis("2026-09-28T20:05:00")
        repo.complete(routine,date,tick,zone)
        repo.complete(routine,date,tick+60000,zone)
        val actual=db.dayLogDao().getAll().single().toEntry()
        assertEquals(1205,actual.startMinute); assertEquals(1205,actual.endMinute)
        assertEquals(DayLogSource.ROUTINE,actual.source); assertNull(actual.taskId)
        logs.delete(actual.id,"default")
        repo.complete(routine,date,tick+120000,zone)
        assertTrue(db.dayLogDao().getAll().single().deleted)
    }
    @Test fun routineWithoutSelectedProfileDoesNotLogAndSessionRetainsTaskLink() = runTest {
        active=null
        val repo=RoutineRepository(db.routineDao(),logs,db.childProfileDao())
        val routine=repo.save(RoutineEntity(title="Routine",emoji="x",deadlineMinutes=1200,repeatDaysMask=127))
        repo.complete(routine,LocalDate.parse("2026-09-28"),1000,zone)
        assertTrue(db.dayLogDao().getAll().isEmpty())
        val sessions=SessionRepository(db.sessionDao())
        RecordSessionUseCase(sessions)(60,true,System.currentTimeMillis(),scheduledTaskId=42,profileId="frozen-profile")
        val session=db.sessionDao().getAllForSync().single()
        assertEquals(42L,session.scheduledTaskId); assertEquals("frozen-profile",session.childProfileId)
    }
    @Test fun daoUpsertKeepsNewerRowsAndEqualVersionTombstonesWin() = runTest {
        val e=DayLogEntry(profileId="default",date=LocalDate.parse("2026-09-28"),name="Task",category=DayLogCategory.OTHER,startMinute=600,endMinute=660,source=DayLogSource.MANUAL,createdAt=1000)
        db.dayLogDao().merge(DayLogEntryEntity.fromEntry(e.copy(updatedAt=3000,deleted=true)))
        db.dayLogDao().merge(DayLogEntryEntity.fromEntry(e.copy(updatedAt=2000)))
        assertTrue(logs.get(e.id)!!.deleted)
        db.dayLogDao().merge(DayLogEntryEntity.fromEntry(e.copy(updatedAt=3000)))
        assertTrue(logs.get(e.id)!!.deleted)
    }
    @Test fun aiBatchIsAtomicAndUndoUsesTombstonesWithoutDeletingOtherEntries() = runTest {
        fun entry(name:String)=DayLogEntry(profileId="default",date=LocalDate.parse("2026-09-29"),name=name,
            category=DayLogCategory.OTHER,startMinute=600,endMinute=630,source=DayLogSource.AI,createdAt=1000)
        val old=entry("Old");logs.add(old)
        val batch=listOf(entry("First"),entry("Second"));logs.addAiBatch(batch)
        logs.undoAiBatch(batch)
        assertFalse(logs.get(old.id)!!.deleted)
        batch.forEach { assertTrue(logs.get(it.id)!!.deleted);assertTrue(logs.get(it.id)!!.updatedAt>it.updatedAt) }
        val new=entry("New")
        assertTrue(runCatching { logs.addAiBatch(listOf(new,old)) }.isFailure)
        assertNull(logs.get(new.id))
    }
    @Test fun undoCannotOverwriteAManualEditOrDeletePartOfAChangedAiBatch() = runTest {
        val batch=(1..2).map { DayLogEntry(profileId="default",date=LocalDate.parse("2026-09-29"),name="AI $it",
            category=DayLogCategory.OTHER,startMinute=600,source=DayLogSource.AI,createdAt=1000) }
        logs.addAiBatch(batch);logs.edit(batch.first().copy(name="Parent edited",updatedAt=2000))
        assertTrue(runCatching { logs.undoAiBatch(batch) }.isFailure)
        assertEquals("Parent edited",logs.get(batch.first().id)!!.name)
        assertFalse(logs.get(batch.last().id)!!.deleted)
    }

}
