package com.kidfocus.timer.data.daylog

import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.kidfocus.timer.data.database.DayLogDao
import com.kidfocus.timer.data.database.DayLogEntryEntity
import com.kidfocus.timer.domain.daylog.newerDayLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(FlowPreview::class)
class DayLogSyncManager @Inject constructor(
    private val account: FirebaseAccountRepository, private val dao: DayLogDao, private val ownership: DayLogOwnership,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var started = false
    private var listener: ListenerRegistration? = null
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()
    fun start() {
        if (started) return
        started = true
        scope.launch {
            account.account.map { it.userId }.distinctUntilChanged().collectLatest { uid ->
                listener?.remove(); listener = null
                _error.value = false
                if (uid == null) return@collectLatest
                val collection = collection(uid) ?: return@collectLatest
                try {
                    // Missing collections are empty; never delete local rows because a snapshot is empty.
                    val snapshot = collection.get(Source.SERVER).await()
                    receive(uid, snapshot.documents.mapNotNull { dayLogFromCloud(it.id, it.data) })
                } catch (e: Exception) { if (e is CancellationException) throw e; _error.value = true }
                listener = collection.addSnapshotListener { snapshot, error ->
                    if (account.account.value.userId != uid) return@addSnapshotListener
                    if (error != null) { _error.value = true; return@addSnapshotListener }
                    if (snapshot != null) scope.launch {
                        receive(uid, snapshot.documents.mapNotNull { dayLogFromCloud(it.id, it.data) })
                        upload(uid) // A new server snapshot also retries pending offline edits.
                    }
                }
                dao.observeAll().debounce(1_200).collect { upload(uid) }
            }
        }
    }
    fun syncNow() { account.account.value.userId?.let { uid -> scope.launch { upload(uid) } } }
    internal suspend fun receive(uid: String, rows: List<com.kidfocus.timer.domain.daylog.DayLogEntry>) = mutex.withLock {
        if (account.account.value.userId != uid) return@withLock
        for (entry in rows) {
            val owner = ownership.owner(entry.id)
            if (owner != null && owner != uid) continue
            ownership.claim(entry.id, uid)
            dao.merge(DayLogEntryEntity.fromEntry(entry))
            ownership.acknowledge(entry, uid)
        }
    }
    internal suspend fun upload(uid: String) = mutex.withLock {
        try {
            val collection = collection(uid) ?: return@withLock
            for (row in dao.getAll()) {
                if (account.account.value.userId != uid) return@withLock
                if (!ownership.visible(row.id, uid)) continue
                ownership.claim(row.id, uid)
                val entry = row.toEntry()
                if (ownership.wasUploaded(entry, uid)) continue
                val reference = collection.document(row.id)
                val winner = account.database()!!.runTransaction { transaction ->
                    val snapshot = transaction.get(reference)
                    val remote = dayLogFromCloud(snapshot.id, snapshot.data)
                    val chosen = newerDayLog(remote, entry)
                    if (chosen == entry && chosen != remote) transaction.set(reference, entry.toDayLogCloud())
                    chosen
                }.await()
                if (account.account.value.userId != uid) return@withLock
                dao.merge(DayLogEntryEntity.fromEntry(winner))
                ownership.acknowledge(winner, uid)
            }
            _error.value = false
        } catch (e: Exception) { if (e is CancellationException) throw e; _error.value = true }
    }
    private fun collection(uid: String) = account.database()?.collection("users")?.document(uid)?.collection("dayLogs")
}
