package com.kidfocus.timer.data.daylog

import android.app.Application
import androidx.room.Room
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import com.kidfocus.timer.data.cloud.*
import com.kidfocus.timer.data.database.*
import com.kidfocus.timer.domain.daylog.*
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class DayLogSyncTest {
    private lateinit var db: SessionDatabase
    private lateinit var sync: DayLogSyncManager
    private lateinit var ownership: DayLogOwnership
    private val account=mockk<FirebaseAccountRepository>()
    private val firestore=mockk<FirebaseFirestore>()
    private val user=MutableStateFlow(CloudAccount(true,"a"))
    private val remote=mutableMapOf<String,Map<String,Any?>>()
    private var writes=0
    private var transactions=0
    private val entry=DayLogEntry(profileId="default",date=LocalDate.parse("2026-09-28"),name="Actual",category=DayLogCategory.OTHER,startMinute=600,endMinute=660,source=DayLogSource.MANUAL,createdAt=1000)
    @Before fun setup() {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("day_log_sync",0).edit().clear().commit()
        db=Room.inMemoryDatabaseBuilder(app,SessionDatabase::class.java).allowMainThreadQueries().build()
        ownership=DayLogOwnership(app)
        every { account.account } returns user
        every { account.database() } returns firestore
        every { firestore.collection("users") } answers {
            val users=mockk<CollectionReference>()
            every { users.document(any()) } answers {
                val uid=firstArg<String>(); val parent=mockk<DocumentReference>()
                every { parent.collection("dayLogs") } answers {
                    val collection=mockk<CollectionReference>()
                    every { collection.document(any()) } answers {
                        val id=firstArg<String>(); val reference=mockk<DocumentReference>()
                        every { reference.path } returns "users/$uid/dayLogs/$id"
                        every { reference.id } returns id
                        reference
                    };collection
                };parent
            };users
        }
        every { firestore.runTransaction(any<Transaction.Function<DayLogEntry>>()) } answers {
            transactions++
            val transaction=mockk<Transaction>()
            every { transaction.get(any()) } answers {
                val ref=firstArg<DocumentReference>(); val snapshot=mockk<DocumentSnapshot>()
                every { snapshot.id } returns ref.id
                every { snapshot.data } answers { remote[ref.path] }
                snapshot
            }
            every { transaction.set(any(),any()) } answers {
                val ref=firstArg<DocumentReference>(); @Suppress("UNCHECKED_CAST") val value=secondArg<Map<String,Any?>>()
                remote[ref.path]=value;writes++;transaction
            }
            Tasks.forResult(firstArg<Transaction.Function<DayLogEntry>>().apply(transaction))
        }
        sync=DayLogSyncManager(account,db.dayLogDao(),ownership)
    }
    @After fun close() { db.close() }
    @Test fun missingCollectionDoesNotEraseLocalAndUploadsOnlyPerRecordDocuments()=runTest {
        db.dayLogDao().merge(DayLogEntryEntity.fromEntry(entry))
        sync.receive("a",emptyList())
        assertEquals(1,db.dayLogDao().getAll().size)
        sync.upload("a")
        assertEquals(1,writes);assertEquals(entry,dayLogFromCloud(entry.id,remote["users/a/dayLogs/${entry.id}"]))
        sync.upload("a");assertEquals(1,transactions)
    }
    @Test fun remoteNewerTombstoneWinsOverStaleClientAndLocalNewerDeleteUploads()=runTest {
        db.dayLogDao().merge(DayLogEntryEntity.fromEntry(entry))
        remote["users/a/dayLogs/${entry.id}"]=entry.copy(updatedAt=2000,deleted=true).toDayLogCloud()
        sync.upload("a")
        assertTrue(db.dayLogDao().get(entry.id)!!.deleted);assertEquals(0,writes)
        db.dayLogDao().merge(DayLogEntryEntity.fromEntry(entry.copy(updatedAt=3000,deleted=true)))
        sync.upload("a")
        assertEquals(3000L,remote.values.single()["updated_at"]);assertEquals(true,remote.values.single()["deleted"])
    }
    @Test fun newerRemoteEditAndStaleRemoteOrMissingRecordsNeverRollBackLocal()=runTest {
        db.dayLogDao().merge(DayLogEntryEntity.fromEntry(entry.copy(updatedAt=3000,name="Local new")))
        sync.receive("a",listOf(entry.copy(updatedAt=2000)))
        assertEquals("Local new",db.dayLogDao().get(entry.id)!!.name)
        sync.receive("a",listOf(entry.copy(updatedAt=4000,name="Remote new")))
        assertEquals("Remote new",db.dayLogDao().get(entry.id)!!.name)
        sync.receive("a",emptyList());assertEquals(1,db.dayLogDao().getAll().size)
    }
    @Test fun accountSwitchDoesNotUploadOrApplyAnotherOwnersRecords()=runTest {
        db.dayLogDao().merge(DayLogEntryEntity.fromEntry(entry));ownership.claim(entry.id,"a")
        user.value=CloudAccount(true,"b")
        sync.upload("a");sync.upload("b")
        sync.receive("a",listOf(entry.copy(updatedAt=4000)))
        assertEquals(0,transactions);assertEquals(1000L,db.dayLogDao().get(entry.id)!!.updatedAt)
        assertFalse(ownership.visible(entry.id,"b"))
    }
    @Test fun equalTimestampLocalDeleteStillUploadsAfterReceivingStaleLiveSnapshot()=runTest {
        db.dayLogDao().merge(DayLogEntryEntity.fromEntry(entry))
        sync.upload("a")
        val deleted=entry.copy(deleted=true)
        db.dayLogDao().merge(DayLogEntryEntity.fromEntry(deleted))
        sync.receive("a",listOf(entry))
        sync.upload("a")
        assertTrue(dayLogFromCloud(entry.id,remote["users/a/dayLogs/${entry.id}"])!!.deleted)
        assertEquals(2,writes)
        sync.upload("a");assertEquals(2,transactions)
    }
    @Test fun malformedRemoteRowsAreRejectedAndMidnightRoundTrips() {
        assertNull(dayLogFromCloud(entry.id,entry.toDayLogCloud()+mapOf("start_minute" to 5000000000L)))
        assertNull(dayLogFromCloud(entry.id,entry.toDayLogCloud()+mapOf("source" to "UNKNOWN")))
        assertNull(dayLogFromCloud(entry.id,null))
        val overnight=entry.copy(startMinute=1430,endMinute=30)
        assertEquals(overnight,dayLogFromCloud(overnight.id,overnight.toDayLogCloud()))
    }
}
