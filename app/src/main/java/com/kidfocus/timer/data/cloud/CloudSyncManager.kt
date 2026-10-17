package com.kidfocus.timer.data.cloud

import android.content.Context
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.kidfocus.timer.alarm.AlarmScheduler
import com.kidfocus.timer.data.database.ChildProfileDao
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.database.RoutineCompletionEntity
import com.kidfocus.timer.data.database.RoutineCompletionPolicy
import com.kidfocus.timer.data.database.RoutineDao
import com.kidfocus.timer.data.database.RoutineEntity
import com.kidfocus.timer.data.database.ScheduledTaskDao
import com.kidfocus.timer.data.database.ScheduledTaskEntity
import com.kidfocus.timer.data.database.SessionDao
import com.kidfocus.timer.data.database.SessionEntity
import com.kidfocus.timer.data.database.SyncRecordDao
import com.kidfocus.timer.data.database.SyncRecordEntity
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.domain.model.AppTheme
import com.kidfocus.timer.domain.model.AgeBand
import com.kidfocus.timer.service.RoutineAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

sealed interface CloudSyncStatus {
    data object LocalOnly : CloudSyncStatus
    data object SignedOut : CloudSyncStatus
    data object Syncing : CloudSyncStatus
    data class Synced(val atMillis: Long) : CloudSyncStatus
    data class Error(val message: String) : CloudSyncStatus
}

/** Room remains the local source of truth; sync_records stores durable per-item versions and tombstones. */
@Singleton
@OptIn(kotlinx.coroutines.FlowPreview::class)
class CloudSyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val accountRepository: FirebaseAccountRepository,
    private val sessionDao: SessionDao,
    private val scheduledTaskDao: ScheduledTaskDao,
    private val routineDao: RoutineDao,
    private val settingsDataStore: SettingsDataStore,
    private val alarmScheduler: AlarmScheduler,
    private val routineAlarmScheduler: RoutineAlarmScheduler,
    private val childProfileDao: ChildProfileDao,
    private val syncRecordDao: SyncRecordDao,
    private val schedulePlans: com.kidfocus.timer.data.schedule.SchedulePlansRepository,
    private val dayLogSync: com.kidfocus.timer.data.daylog.DayLogSyncManager,
    private val scheduleAnchors: com.kidfocus.timer.data.schedule.ScheduleAnchorsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val preferences = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
    private val deviceId = preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString("device_id", it).apply()
    }
    private val _status = MutableStateFlow<CloudSyncStatus>(CloudSyncStatus.LocalOnly)
    val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()
    private val _oldAppWarning = MutableStateFlow(false)
    val oldAppWarning: StateFlow<Boolean> = _oldAppWarning.asStateFlow()
    val account: StateFlow<CloudAccount> = accountRepository.account
    private var started = false
    private var activeUid: String? = null
    private var ready = false
    private var applyingRemote = false
    private var migratedAtMillis = 0L
    private val listeners = mutableListOf<ListenerRegistration>()
    private val pendingCompletions = mutableMapOf<String, Triple<String, String, Map<String, Any?>>>()

    fun start() {
        if (started) return
        started = true
        accountRepository.start()
        dayLogSync.start()
        observeAccount()
        observeLocalChanges()
    }

    suspend fun signIn(email: String, password: String) = accountRepository.signIn(email, password)
    suspend fun createAccount(email: String, password: String) = accountRepository.createAccount(email, password)
    suspend fun signInWithGoogle(activityContext: Context) = accountRepository.signInWithGoogle(activityContext)
    suspend fun sendPasswordReset(email: String) = accountRepository.sendPasswordReset(email)
    suspend fun signOut() = accountRepository.signOut()
    suspend fun deleteAccount() = accountRepository.deleteAccount()

    fun syncNow() {
        dayLogSync.syncNow()
        activeUid?.let { uid -> scope.launch { upload(uid) } }
    }

    fun reimportOldAppSnapshot() {
        activeUid?.let { uid -> scope.launch {
            mutex.withLock {
                try {
                    val backup = backupRef(uid).get(Source.SERVER).await()
                    val data = backup.data.asStringMap() ?: return@withLock
                    if ((data["updatedAtMillis"] as? Number)?.toLong() ?: 0L <= migratedAtMillis) return@withLock
                    val migrated = SyncV2Model.migrateV1(data, System.currentTimeMillis(), deviceId)
                    for (doc in migrated.docs) importCandidate(uid, doc.collection, doc.id, doc.data)
                    migrated.settings?.let { importCandidate(uid, "settings", "settings", it) }
                    migratedAtMillis = (data["updatedAtMillis"] as Number).toLong()
                    metaRef(uid).set(mapOf("schemaVersion" to 2, "migratedAtMillis" to migratedAtMillis,
                        "migratedBy" to deviceId)).await()
                    _oldAppWarning.value = false
                    _status.value = CloudSyncStatus.Synced(System.currentTimeMillis())
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _status.value = CloudSyncStatus.Error(error.userMessage())
                }
            }
        } }
    }

    private fun observeAccount() {
        scope.launch {
            account.collectLatest { current ->
                listeners.forEach(ListenerRegistration::remove); listeners.clear()
                pendingCompletions.clear()
                ready = false; activeUid = current.userId; _oldAppWarning.value = false
                when {
                    !current.configured -> _status.value = CloudSyncStatus.LocalOnly
                    current.userId == null -> _status.value = CloudSyncStatus.SignedOut
                    else -> initialize(current.userId)
                }
            }
        }
    }

    private val coreRevision = combine(sessionDao.observeAllForSync(), scheduledTaskDao.getAllFlow(),
        routineDao.observeAll(), routineDao.observeAllCompletions(), settingsDataStore.settingsFlow
    ) { sessions, tasks, routines, completions, settings ->
        listOf(sessions.hashCode(), tasks.hashCode(), routines.hashCode(), completions.hashCode(),
            settings.cloudHashCode()).hashCode()
    }
    private val localRevision = combine(coreRevision, childProfileDao.observeAll(), scheduleAnchors.all,
        schedulePlans.all) { core, profiles, anchors, plans ->
        listOf(core, profiles.hashCode(), anchors.hashCode(), plans.hashCode()).hashCode()
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    private fun observeLocalChanges() {
        scope.launch {
            localRevision.debounce(1_200L).collect { _ ->
                val uid = activeUid
                if (uid != null && ready && !applyingRemote) upload(uid)
            }
        }
    }

    private suspend fun initialize(uid: String) {
        _status.value = CloudSyncStatus.Syncing
        try {
            val firstMigration = migrateOnce(uid)
            // Detect edits made just before process death before a remote pull can overwrite Room.
            // On the first v1 migration there are no records yet, but a prior v1 sync marker proves ownership.
            if (firstMigration && preferences.getLong("last_remote_$uid", 0) > 0) scanLocal(uid)
            else scanLocal(uid, onlyTracked = true)
            for (collection in SyncV2Model.collections) {
                val ref = userRef(uid).collection(collection)
                val query = if (collection == "sessions") ref.whereGreaterThanOrEqualTo("timestampMillis",
                    System.currentTimeMillis() - SESSION_WINDOW_MILLIS) else ref
                for (doc in query.get(Source.SERVER).await().documents) receive(uid, collection, doc.id, doc.data)
            }
            val settings = settingsRef(uid).get(Source.SERVER).await()
            if (settings.exists()) receive(uid, "settings", "settings", settings.data)
            val backup = backupRef(uid).get(Source.SERVER).await()
            updateOldAppWarning(backup.data)
            ready = true
            upload(uid)
            listen(uid)
            if (_status.value is CloudSyncStatus.Syncing) _status.value = CloudSyncStatus.Synced(System.currentTimeMillis())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // The local queue is durable; reconnect or manual retry will flush it.
            scanLocal(uid)
            ready = true
            listen(uid)
            _status.value = CloudSyncStatus.Error(error.userMessage())
        }
    }

    private suspend fun migrateOnce(uid: String): Boolean {
        val meta = metaRef(uid)
        val current = accountRepository.database()!!.runTransaction { it.get(meta).data }.await()
        if ((current?.get("schemaVersion") as? Number)?.toInt() == 2) {
            migratedAtMillis = (current["migratedAtMillis"] as? Number)?.toLong() ?: 0
            return false
        }
        val backup = backupRef(uid).get(Source.SERVER).await()
        val snapshot = backup.data.asStringMap()
        val now = System.currentTimeMillis()
        val migrated = if (snapshot != null && (snapshot["schemaVersion"] as? Number)?.toInt() == 1)
            SyncV2Model.migrateV1(snapshot, now, deviceId) else null
        val writes = migrated?.docs?.map { doc -> itemRef(uid, doc.collection, doc.id) to doc.data }.orEmpty() +
            listOfNotNull(migrated?.settings?.let { settingsRef(uid) to it })
        for (part in writes.chunked(450)) {
            val batch = accountRepository.database()!!.batch()
            part.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().await()
        }
        val at = (snapshot?.get("updatedAtMillis") as? Number)?.toLong() ?: now
        val metaData = mapOf("schemaVersion" to 2, "migratedAtMillis" to at, "migratedBy" to deviceId)
        accountRepository.database()!!.runTransaction { transaction ->
            val existing = transaction.get(meta)
            if ((existing.get("schemaVersion") as? Number)?.toInt() != 2) transaction.set(meta, metaData)
        }.await()
        migratedAtMillis = at
        return true
    }

    private fun listen(uid: String) {
        listeners.forEach(ListenerRegistration::remove); listeners.clear()
        for (collection in SyncV2Model.collections) {
            val ref = userRef(uid).collection(collection)
            val query = if (collection == "sessions") ref.whereGreaterThanOrEqualTo("timestampMillis",
                System.currentTimeMillis() - SESSION_WINDOW_MILLIS) else ref
            listeners += query.addSnapshotListener { snapshot, error ->
                if (uid != activeUid) return@addSnapshotListener
                if (error != null) { _status.value = CloudSyncStatus.Error(error.userMessage()); return@addSnapshotListener }
                snapshot?.documentChanges?.filter { it.type != DocumentChange.Type.REMOVED }?.forEach { change ->
                    scope.launch {
                        mutex.withLock {
                            scanLocal(uid)
                            receive(uid, collection, change.document.id, change.document.data)
                        }
                        upload(uid)
                    }
                }
                if (snapshot != null && !snapshot.metadata.isFromCache) scope.launch { upload(uid) }
            }
        }
        listeners += settingsRef(uid).addSnapshotListener { snapshot, error ->
            if (uid != activeUid) return@addSnapshotListener
            if (error != null) { _status.value = CloudSyncStatus.Error(error.userMessage()); return@addSnapshotListener }
            if (snapshot?.exists() == true) scope.launch {
                mutex.withLock {
                    scanLocal(uid)
                    receive(uid, "settings", "settings", snapshot.data)
                }
            }
        }
        listeners += backupRef(uid).addSnapshotListener { snapshot, error ->
            if (uid == activeUid && error == null) updateOldAppWarning(snapshot?.data)
        }
    }

    private fun updateOldAppWarning(raw: Map<String, Any>?) {
        _oldAppWarning.value = ((raw?.get("updatedAtMillis") as? Number)?.toLong() ?: 0) > migratedAtMillis
    }

    private suspend fun upload(uid: String) = mutex.withLock {
        if (uid != activeUid || !ready) return@withLock
        _status.value = CloudSyncStatus.Syncing
        try {
            scanLocal(uid)
            for (record in syncRecordDao.dirty(uid)) {
                if (uid != activeUid) return@withLock
                val localData = SyncV2Json.decode(record.payloadJson)
                val ref = itemRef(uid, record.collection, record.docId)
                val winnerData = accountRepository.database()!!.runTransaction { transaction ->
                    val snapshot = transaction.get(ref)
                    val remoteData = snapshot.data.asStringMap()
                    val local = parsed(record.collection, record.docId, localData)
                    val remote = remoteData?.let { parsed(record.collection, record.docId, it) }
                    if (remote == null || SyncV2Model.pickWinner(record.collection, local, remote) == "local") {
                        transaction.set(ref, localData)
                        localData
                    } else remoteData
                }.await()
                if (winnerData == localData) syncRecordDao.upsert(record.copy(dirty = false))
                else receive(uid, record.collection, record.docId, winnerData)
            }
            _status.value = CloudSyncStatus.Synced(System.currentTimeMillis())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _status.value = CloudSyncStatus.Error(error.userMessage())
        }
    }

    private suspend fun scanLocal(uid: String, onlyTracked: Boolean = false) {
        val entities = linkedMapOf<String, Map<String, Any?>>()
        fun add(collection: String, row: Map<String, Any?>) {
            val doc = SyncV2Model.toDoc(collection, row, 0, deviceId) ?: return
            entities["$collection/${doc.id}"] = doc.data
        }
        childProfileDao.getAllForSync().forEach { add("profiles", it.toCloudMap()) }
        scheduledTaskDao.getAllForSync().forEach { add("tasks", it.toCloudMap()) }
        routineDao.getAllForSync().forEach { add("routines", it.toCloudMap()) }
        sessionDao.getAllForSync().filter { it.timestampMillis >= System.currentTimeMillis() - SESSION_WINDOW_MILLIS }
            .forEach { add("sessions", it.toCloudMap()) }
        routineDao.getAllCompletionsForSync().forEach { add("completions", it.toCloudMap()) }
        val settings = settingsDataStore.settingsFlow.first()
        entities["settings/settings"] = SyncV2Model.parseSettings(settings.toCloudMap() + mapOf(
            "scheduleAnchors" to scheduleAnchors.all.first().mapValues { (_, value) ->
                com.kidfocus.timer.data.schedule.ScheduleJson.anchorsMap(value) },
            "schedulePlans" to schedulePlans.all.first().mapValues { (_, plan) ->
                plan?.let(com.kidfocus.timer.data.schedule.SchedulePlanJson::toMap) },
        ))
        val prior = syncRecordDao.forUser(uid).associateBy { "${it.collection}/${it.docId}" }
        for ((key, data) in entities) {
            val collection = key.substringBefore('/'); val id = key.substringAfter('/')
            val entity = data.filterKeys { it !in META_FIELDS }
            val fingerprint = SyncV2Json.canonical(entity)
            val old = prior[key]
            if (onlyTracked && old == null) continue
            if (old == null || old.deleted || old.fingerprint != fingerprint) {
                val now = maxOf(System.currentTimeMillis(), (old?.updatedAtMillis ?: 0) + 1)
                val doc = if (collection == "settings") entity + mapOf("updatedAtMillis" to now,
                    "deviceId" to deviceId, "schemaVersion" to 2)
                else SyncV2Model.toDoc(collection, entity, now, deviceId)!!.data
                syncRecordDao.upsert(SyncRecordEntity(uid, collection, id, SyncV2Json.encode(doc), fingerprint,
                    now, deviceId, false, true))
            }
        }
        for ((key, old) in prior) {
            if (key in entities || old.deleted || old.collection == "settings") continue
            if (old.collection == "sessions" &&
                ((SyncV2Json.decode(old.payloadJson)["timestampMillis"] as? Number)?.toLong() ?: 0) <
                System.currentTimeMillis() - SESSION_WINDOW_MILLIS) continue
            val now = maxOf(System.currentTimeMillis(), old.updatedAtMillis + 1)
            val doc = SyncV2Json.decode(old.payloadJson) + mapOf("updatedAtMillis" to now,
                "deviceId" to deviceId, "deleted" to true)
            syncRecordDao.upsert(old.copy(payloadJson = SyncV2Json.encode(doc), updatedAtMillis = now,
                deviceId = deviceId, deleted = true, dirty = true))
        }
    }

    private fun parsed(collection: String, id: String, data: Map<String, Any?>): SyncV2Model.Parsed? =
        if (collection == "settings") {
            if ((data["schemaVersion"] as? Number)?.toInt() != 2) null else SyncV2Model.Parsed(id,
                SyncV2Model.parseSettings(data), (data["updatedAtMillis"] as? Number)?.toLong() ?: 0,
                data["deviceId"] as? String ?: "", false)
        } else SyncV2Model.fromDoc(collection, id, data)

    private suspend fun receive(uid: String, collection: String, id: String, raw: Map<String, Any?>?) {
        if (uid != activeUid) return
        val data = raw.asStringMap() ?: return
        val remote = parsed(collection, id, data) ?: return
        if (collection == "completions" && !remote.deleted &&
            routineDao.getById((remote.entity["routineId"] as Number).toLong()) == null) {
            pendingCompletions[id] = Triple(uid, id, data)
            return
        }
        val localRecord = syncRecordDao.get(uid, collection, id)
        val local = localRecord?.let { parsed(collection, id, SyncV2Json.decode(it.payloadJson)) }
        if (local != null && SyncV2Model.pickWinner(collection, local, remote) == "local") {
            if (!localRecord.dirty &&
                SyncV2Json.canonical(data) != SyncV2Json.canonical(SyncV2Json.decode(localRecord.payloadJson))) {
                syncRecordDao.upsert(localRecord.copy(dirty = true))
            }
            return
        }
        applyingRemote = true
        try {
            if (collection == "settings") applySettings(remote.entity)
            else applyEntity(collection, remote)
            val fingerprint = SyncV2Json.canonical(remote.entity)
            syncRecordDao.upsert(SyncRecordEntity(uid, collection, id, SyncV2Json.encode(data), fingerprint,
                remote.updatedAtMillis, remote.deviceId, remote.deleted, false))
        } finally { applyingRemote = false }
    }

    private suspend fun applyEntity(collection: String, row: SyncV2Model.Parsed) {
        val entity = row.entity
        when (collection) {
            "tasks" -> {
                val id = (entity["id"] as Number).toLong()
                val previous = scheduledTaskDao.getById(id)
                previous?.let { runCatching { alarmScheduler.cancelTask(it.toDomain()) } }
                if (row.deleted) scheduledTaskDao.deleteById(id)
                else taskFromCloud(entity)?.copy(photoUri = previous?.photoUri)?.let { task ->
                    scheduledTaskDao.insert(task)
                    runCatching { alarmScheduler.scheduleTask(task.toDomain()) }
                }
            }
            "routines" -> {
                val id = (entity["id"] as Number).toLong()
                routineAlarmScheduler.cancel(id)
                if (row.deleted) routineDao.deleteById(id)
                else routineFromCloud(entity)?.copy(photoUri = routineDao.getById(id)?.photoUri)?.let { routine ->
                    if (routineDao.getById(id) == null) routineDao.insert(routine) else routineDao.update(routine)
                    routineAlarmScheduler.schedule(routine)
                    val pending = pendingCompletions.values.filter { (_, docId, data) ->
                        docId.startsWith("${id}_") && (data["routineId"] as? Number)?.toLong() == id
                    }
                    pending.forEach { (uid, docId, data) ->
                        pendingCompletions.remove(docId)
                        receive(uid, "completions", docId, data)
                    }
                }
            }
            "sessions" -> {
                val id = (entity["id"] as Number).toLong()
                if (row.deleted) sessionDao.deleteById(id)
                else sessionFromCloud(entity)?.let { remote ->
                    val previous = sessionDao.getById(id)
                    sessionDao.insertSessions(listOf(remote.copy(scheduledTaskId = previous?.scheduledTaskId)))
                }
            }
            "profiles" -> {
                if (row.deleted) childProfileDao.deleteById(row.id)
                else profileFromCloud(entity)?.let { childProfileDao.upsert(it) }
            }
            "completions" -> {
                val routineId = (entity["routineId"] as Number).toLong()
                val date = entity["occurrenceDate"] as String
                if (row.deleted) routineDao.deleteCompletion(routineId, date)
                else if (routineDao.getById(routineId) != null) {
                    val existing = routineDao.getCompletion(routineId, date)
                    routineDao.saveCompletionRaw(RoutineCompletionEntity(
                        id = existing?.id ?: 0, routineId = routineId, occurrenceDate = date,
                        scheduledDeadlineMillis = (entity["scheduledDeadlineMillis"] as Number).toLong(),
                        completedAtMillis = (entity["completedAtMillis"] as Number).toLong(),
                        status = entity["status"] as String,
                    ))
                }
            }
        }
    }

    private suspend fun applySettings(cloud: Map<String, Any?>) {
        scheduleAnchors.applyRemote(cloud["scheduleAnchors"])
        schedulePlans.applyRemote(cloud["schedulePlans"])
        val local = settingsDataStore.settingsFlow.first()
        settingsDataStore.saveSettings(local.copy(
            focusDurationMinutes = (cloud["focusDurationMinutes"] as Number).toInt(),
            breakDurationMinutes = (cloud["breakDurationMinutes"] as Number).toInt(),
            dailyGoalMinutes = (cloud["dailyGoalMinutes"] as Number).toInt(),
            appTheme = AppTheme.fromKey(cloud["appTheme"] as String),
            soundEnabled = cloud["soundEnabled"] as Boolean,
            vibrationEnabled = cloud["vibrationEnabled"] as Boolean,
            learningAgeBand = (cloud["learningAgeBand"] as String).takeIf(AgeBand::isValid) ?: local.learningAgeBand,
            calmModeEnabled = cloud["calmModeEnabled"] as Boolean,
            allowChildExtendFocus = cloud["allowChildExtendFocus"] as Boolean,
            maxExtraFocusMinutes = (cloud["maxExtraFocusMinutes"] as Number).toInt()
                .takeIf { it in com.kidfocus.timer.domain.model.FocusTimePolicy.extraChoices } ?: local.maxExtraFocusMinutes,
        ))
    }

    private suspend fun importCandidate(uid: String, collection: String, id: String, data: Map<String, Any?>) {
        val ref = itemRef(uid, collection, id)
        val chosen = accountRepository.database()!!.runTransaction { transaction ->
            val remote = transaction.get(ref).data.asStringMap()
            val candidate = parsed(collection, id, data)
            val current = remote?.let { parsed(collection, id, it) }
            if (current == null || SyncV2Model.pickWinner(collection, candidate, current) == "local") {
                transaction.set(ref, data); data
            } else remote
        }.await()
        receive(uid, collection, id, chosen)
    }

    private fun userRef(uid: String) = accountRepository.database()!!.collection("users").document(uid)
    private fun itemRef(uid: String, collection: String, id: String): DocumentReference =
        if (collection == "settings") settingsRef(uid) else userRef(uid).collection(collection).document(id)
    private fun settingsRef(uid: String) = userRef(uid).collection("sync").document("settings")
    private fun metaRef(uid: String) = userRef(uid).collection("sync").document("meta")
    private fun backupRef(uid: String) = userRef(uid).collection("backups").document("current")
    private fun Throwable.userMessage(): String = when {
        message?.contains("password", ignoreCase = true) == true -> "Email hoặc mật khẩu chưa đúng"
        message?.contains("network", ignoreCase = true) == true -> "Chưa có mạng; sẽ thử đồng bộ lại sau"
        else -> message ?: "Không thể đồng bộ dữ liệu"
    }
    private companion object {
        const val SESSION_WINDOW_MILLIS = 400L * 24 * 60 * 60 * 1000
        val META_FIELDS = setOf("updatedAtMillis", "deviceId", "deleted", "schemaVersion")
    }
}

private fun Map<*, *>?.asStringMap(): Map<String, Any?>? = this?.entries?.associate { (key, value) -> key.toString() to value }

private fun com.kidfocus.timer.domain.model.TimerSettings.cloudHashCode() = listOf(
    focusDurationMinutes, breakDurationMinutes, appTheme, soundEnabled,
    vibrationEnabled, dailyGoalMinutes,
    learningAgeBand, calmModeEnabled, allowChildExtendFocus, maxExtraFocusMinutes,
).hashCode()

private fun com.kidfocus.timer.domain.model.TimerSettings.toCloudMap() = mapOf(
    "focusDurationMinutes" to focusDurationMinutes,
    "breakDurationMinutes" to breakDurationMinutes,
    "appTheme" to appTheme.name,
    "soundEnabled" to soundEnabled,
    "vibrationEnabled" to vibrationEnabled,
    "dailyGoalMinutes" to dailyGoalMinutes,
    "learningAgeBand" to learningAgeBand,
    "calmModeEnabled" to calmModeEnabled,
    "allowChildExtendFocus" to allowChildExtendFocus,
    "maxExtraFocusMinutes" to maxExtraFocusMinutes,
)

private fun SessionEntity.toCloudMap() = mapOf(
    "id" to id, "durationSeconds" to durationSeconds,
    "isFocus" to isFocus, "timestampMillis" to timestampMillis,
    "childProfileId" to childProfileId,
    "taskId" to taskId, "taskTitle" to taskTitle, "completed" to completed,
)

private fun ScheduledTaskEntity.toCloudMap() = mapOf(
    "id" to id, "taskType" to taskType, "name" to name, "emoji" to emoji,
    "hour" to hour, "minute" to minute, "daysOfWeek" to daysOfWeek,
    "focusDurationMinutes" to focusDurationMinutes,
    "breakDurationMinutes" to breakDurationMinutes,
    "enabled" to enabled, "isCustom" to isCustom,
    "childProfileId" to childProfileId,
)

private fun RoutineEntity.toCloudMap() = mapOf(
    "id" to id, "title" to title, "emoji" to emoji,
    "deadlineMinutes" to deadlineMinutes, "repeatDaysMask" to repeatDaysMask,
    "reminderMinutesBefore" to reminderMinutesBefore, "enabled" to enabled,
    "linkedTimerMinutes" to linkedTimerMinutes, "createdAtMillis" to createdAtMillis,
    "childProfileId" to childProfileId,
)

internal fun ChildProfileEntity.toCloudMap() = mapOf(
    "id" to id,
    "name" to name,
    "avatarEmoji" to avatarEmoji,
    "ageBand" to ageBand,
    "createdAtMillis" to createdAtMillis,
    "archived" to archived,
)

private fun RoutineCompletionEntity.toCloudMap() = mapOf(
    "id" to id, "routineId" to routineId, "occurrenceDate" to occurrenceDate,
    "scheduledDeadlineMillis" to scheduledDeadlineMillis,
    "completedAtMillis" to completedAtMillis, "status" to status,
)

internal fun mergeRoutineCompletions(
    remote: List<RoutineCompletionEntity>,
    local: List<RoutineCompletionEntity>,
    validRoutineIds: Set<Long>,
): List<RoutineCompletionEntity> {
    val key: (RoutineCompletionEntity) -> Pair<Long, String> = {
        it.routineId to it.occurrenceDate
    }
    val localByKey = local.associateBy(key)
    val remoteKeys = remote.mapTo(hashSetOf(), key)
    return buildList {
        remote.forEach { remoteCompletion ->
            val localCompletion = localByKey[key(remoteCompletion)]
            add(
                if (localCompletion == null) remoteCompletion
                else RoutineCompletionPolicy.better(remoteCompletion, localCompletion)
                    ?: remoteCompletion
            )
        }
        local.asSequence()
            .filter { it.routineId in validRoutineIds && key(it) !in remoteKeys }
            .forEach { add(it.copy(id = 0L)) }
    }
}

private fun Map<*, *>.long(key: String, fallback: Long = 0L) =
    (get(key) as? Number)?.toLong() ?: fallback

private fun Map<*, *>.int(key: String, fallback: Int = 0) =
    (get(key) as? Number)?.toInt() ?: fallback

private fun Map<*, *>.string(key: String, fallback: String = "") =
    get(key) as? String ?: fallback

private fun Map<*, *>.bool(key: String, fallback: Boolean = false) =
    get(key) as? Boolean ?: fallback

private fun sessionFromCloud(map: Map<*, *>): SessionEntity? = runCatching {
    SessionEntity(
        id = map.long("id"),
        durationSeconds = map.int("durationSeconds").coerceAtLeast(0),
        isFocus = map.bool("isFocus"),
        timestampMillis = map.long("timestampMillis"),
        childProfileId = map.string("childProfileId", ChildProfileEntity.DEFAULT_ID),
        taskId = (map["taskId"] as? String)?.takeIf { it.isNotBlank() },
        taskTitle = (map["taskTitle"] as? String)?.takeIf { it.isNotBlank() },
        completed = map.bool("completed", true),
    )
}.getOrNull()?.takeIf { it.id > 0 && it.timestampMillis > 0 }

private fun taskFromCloud(map: Map<*, *>): ScheduledTaskEntity? = runCatching {
    ScheduledTaskEntity(
        id = map.long("id"), taskType = map.string("taskType"),
        name = map.string("name"), emoji = map.string("emoji", "📚"),
        hour = map.int("hour").coerceIn(0, 23), minute = map.int("minute").coerceIn(0, 59),
        daysOfWeek = map.string("daysOfWeek"),
        focusDurationMinutes = map.int("focusDurationMinutes", 25).coerceIn(5, 120),
        breakDurationMinutes = map.int("breakDurationMinutes", 5).coerceIn(1, 30),
        enabled = map.bool("enabled", true), isCustom = map.bool("isCustom"),
        childProfileId = map.string("childProfileId", ChildProfileEntity.DEFAULT_ID),
    )
}.getOrNull()?.takeIf { it.id > 0 && it.name.isNotBlank() }

private fun routineFromCloud(map: Map<*, *>): RoutineEntity? = runCatching {
    RoutineEntity(
        id = map.long("id"), title = map.string("title"), emoji = map.string("emoji", "⭐"),
        deadlineMinutes = map.int("deadlineMinutes").coerceIn(0, 1439),
        repeatDaysMask = map.int("repeatDaysMask").coerceIn(1, 127),
        reminderMinutesBefore = map.int("reminderMinutesBefore", 15).coerceIn(0, 180),
        enabled = map.bool("enabled", true),
        linkedTimerMinutes = (map["linkedTimerMinutes"] as? Number)?.toInt()?.coerceIn(5, 120),
        createdAtMillis = map.long("createdAtMillis", System.currentTimeMillis()),
        childProfileId = map.string("childProfileId", ChildProfileEntity.DEFAULT_ID),
    )
}.getOrNull()?.takeIf { it.id > 0 && it.title.isNotBlank() }

internal fun profileFromCloud(map: Map<*, *>): ChildProfileEntity? = runCatching {
    ChildProfileEntity(
        id = map.string("id").take(64),
        name = map.string("name").trim().take(40),
        avatarEmoji = map.string("avatarEmoji", "🐣"),
        ageBand = AgeBand.normalize(map.string("ageBand", AgeBand.DEFAULT_ID)),
        createdAtMillis = map.long("createdAtMillis", System.currentTimeMillis()),
        archived = map.bool("archived"),
    )
}.getOrNull()?.takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
