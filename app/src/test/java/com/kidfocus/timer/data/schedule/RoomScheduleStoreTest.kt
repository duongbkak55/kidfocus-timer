package com.kidfocus.timer.data.schedule

import android.app.Application
import androidx.room.Room
import com.kidfocus.timer.alarm.AlarmScheduler
import com.kidfocus.timer.data.database.ScheduledTaskEntity
import com.kidfocus.timer.data.database.SessionDatabase
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.domain.schedule.*
import io.mockk.*
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RoomScheduleStoreTest {
    private lateinit var database: SessionDatabase
    private lateinit var store: RoomScheduleStore
    private lateinit var settings: SettingsDataStore
    private lateinit var alarms: AlarmScheduler
    private lateinit var anchorsRepository: ScheduleAnchorsRepository
    private val anchorRows = MutableStateFlow<Map<String, String>>(emptyMap())
    private var snapshotJson: String? = null
    private val days = DayOfWeek.entries.toSet()
    private val task = ScheduledTask(12, TaskType.HOMEWORK, "Homework", "", 20, 30, DayCodec.toCalendar(days), 30, 5, photoUri = "content://local/photo")
    private val anchors = ScheduleAnchors(days.associateWith { LocalTime.of(6, 15) }, days.associateWith { LocalTime.of(22, 30) })

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SessionDatabase::class.java).allowMainThreadQueries().build()
        settings = mockk<SettingsDataStore>()
        every { settings.scheduleAnchorsJson } returns anchorRows
        coEvery { settings.saveScheduleAnchorsJson(any()) } coAnswers { anchorRows.value = anchorRows.value + firstArg<Map<String, String>>() }
        coEvery { settings.getScheduleSnapshotJson(any()) } coAnswers { snapshotJson }
        coEvery { settings.saveScheduleSnapshotJson(any(), any()) } coAnswers { snapshotJson = secondArg() }
        alarms = mockk(relaxed = true)
        anchorsRepository = ScheduleAnchorsRepository(settings)
        store = RoomScheduleStore(database, database.scheduledTaskDao(), anchorsRepository, settings, alarms)
    }
    @After fun tearDown() { database.close() }

    @Test fun `apply and undo persist real Room rows and JSON while retaining other profiles`() = runTest {
        val other = task.copy(id = 99, childProfileId = "other")
        database.scheduledTaskDao().insertAll(listOf(task, other).map(ScheduledTaskEntity::fromDomain))
        anchorsRepository.save("default", anchors)
        val before = store.read("default")
        val useCase = ApplyScheduleUseCase(store) { 10_000 }
        useCase.apply("default", before, listOf(ScheduleChange.MoveTask(12, setOf(DayOfWeek.MONDAY), LocalTime.of(19, 0))), "l1")
        assertEquals(2, store.read("default").tasks.size)
        assertEquals(before, store.snapshot("default")!!.state)
        assertEquals(store.read("default"), store.snapshot("default")!!.applied)
        useCase.undo("default")
        assertEquals(before, store.read("default"))
        assertEquals(other, store.read("other").tasks.single())
        assertNull(snapshotJson)
        verify(exactly = 2) { alarms.scheduleAll(any()) }
    }
    @Test fun `SQL failure after first write rolls back Room and preserves anchors snapshot alarms`() = runTest {
        val second = task.copy(id = 13, hour = 19)
        database.scheduledTaskDao().insertAll(listOf(task, second).map(ScheduledTaskEntity::fromDomain))
        anchorsRepository.save("default", anchors)
        val before = store.read("default")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_second BEFORE INSERT ON scheduled_tasks WHEN NEW.id = 13 BEGIN SELECT RAISE(ABORT, 'injected SQL failure'); END")
        assertTrue(runCatching { ApplyScheduleUseCase(store).apply("default", before,
            listOf(ScheduleChange.MoveTask(12, days, LocalTime.of(18, 0))), "l1") }.isFailure)
        assertEquals(before, store.read("default"))
        assertNull(snapshotJson)
        verify(exactly = 0) { alarms.scheduleAll(any()) }
        verify(exactly = 0) { alarms.cancelTask(any()) }
    }
    @Test fun `DataStore failure after write compensates anchors and rolls back Room`() = runTest {
        database.scheduledTaskDao().insert(ScheduledTaskEntity.fromDomain(task))
        anchorsRepository.save("default", anchors)
        val before = store.read("default")
        coEvery { settings.saveScheduleAnchorsJson(any()) } coAnswers {
            val rows = firstArg<Map<String, String>>()
            anchorRows.value = anchorRows.value + rows
            if (ScheduleJson.decodeAnchors(rows.getValue("default")).commuteMinutes == 30) error("injected disk failure")
        }
        assertTrue(runCatching { ApplyScheduleUseCase(store).saveAnchors("default", before, anchors.copy(commuteMinutes = 30)) }.isFailure)
        assertEquals(before, store.read("default"))
        assertNull(snapshotJson)
        verify(exactly = 0) { alarms.scheduleAll(any()) }
    }
    @Test fun `remote missing anchors keep local and valid profile map merges`() = runTest {
        anchorsRepository.save("default", anchors)
        anchorsRepository.applyRemote(null)
        assertEquals(anchors, anchorsRepository.get("default"))
        val remote = anchors.copy(commuteMinutes = 20)
        anchorsRepository.applyRemote(mapOf("other" to ScheduleJson.anchorsMap(remote)))
        assertEquals(anchors, anchorsRepository.get("default"))
        assertEquals(remote, anchorsRepository.get("other"))
    }
}
