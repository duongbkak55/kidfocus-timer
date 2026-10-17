package com.kidfocus.timer.data.database

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SyncRecordQueueTest {
    @Test fun offlineTombstoneSurvivesDatabaseReopen() { runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "sync-record-queue-${System.nanoTime()}.db"
        val record = SyncRecordEntity("owner", "tasks", "42", "{\"deleted\":true}", "task-42",
            200, "phone", deleted = true, dirty = true)
        val first = Room.databaseBuilder(context, SessionDatabase::class.java, name).build()
        try { first.syncRecordDao().upsert(record) } finally { first.close() }
        val second = Room.databaseBuilder(context, SessionDatabase::class.java, name).build()
        try {
            val db = second
            assertEquals(record, db.syncRecordDao().get("owner", "tasks", "42"))
            assertEquals(listOf(record), db.syncRecordDao().dirty("owner"))
            assertTrue(db.syncRecordDao().dirty("someone-else").isEmpty())
        } finally { second.close() }
        context.deleteDatabase(name)
    } }
}
