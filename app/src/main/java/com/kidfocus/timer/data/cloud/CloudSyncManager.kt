package com.kidfocus.timer.data.cloud

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.kidfocus.timer.alarm.AlarmScheduler
import com.kidfocus.timer.data.database.RoutineCompletionEntity
import com.kidfocus.timer.data.database.RoutineCompletionPolicy
import com.kidfocus.timer.data.database.RoutineDao
import com.kidfocus.timer.data.database.RoutineEntity
import com.kidfocus.timer.data.database.ScheduledTaskDao
import com.kidfocus.timer.data.database.ScheduledTaskEntity
import com.kidfocus.timer.data.database.SessionDao
import com.kidfocus.timer.data.database.SessionDatabase
import com.kidfocus.timer.data.database.SessionEntity
import com.kidfocus.timer.data.database.ChildProfileDao
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.domain.model.AppTheme
import com.kidfocus.timer.service.RoutineAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

/**
 * Keeps one compact, user-private Firestore snapshot in sync with Room/DataStore.
 * This is intentionally a temporary backend: simple to operate and easy to migrate later.
 */
@Singleton
@OptIn(kotlinx.coroutines.FlowPreview::class)
class CloudSyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val accountRepository: FirebaseAccountRepository,
    private val database: SessionDatabase,
    private val sessionDao: SessionDao,
    private val scheduledTaskDao: ScheduledTaskDao,
    private val routineDao: RoutineDao,
    private val settingsDataStore: SettingsDataStore,
    private val alarmScheduler: AlarmScheduler,
    private val routineAlarmScheduler: RoutineAlarmScheduler,
    private val childProfileDao: ChildProfileDao,
    private val schedulePlans: com.kidfocus.timer.data.schedule.SchedulePlansRepository,
    private val dayLogSync: com.kidfocus.timer.data.daylog.DayLogSyncManager,
    private val scheduleAnchors: com.kidfocus.timer.data.schedule.ScheduleAnchorsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val syncPreferences = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
    private val deviceId = syncPreferences.let { preferences ->
        preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("device_id", it).apply()
        }
    }
    private val _status = MutableStateFlow<CloudSyncStatus>(CloudSyncStatus.LocalOnly)
    val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()
    val account: StateFlow<CloudAccount> = accountRepository.account

    private var started = false
    private var activeUid: String? = null
    private var readyForLocalUploads = false
    private var applyingRemote = false
    private var lastAppliedRemoteMillis = 0L
    private var lastSyncedLocalRevision: Int? = null
    private var remoteListener: ListenerRegistration? = null
    private var pendingUpload: Job? = null

    fun start() {
        if (started) return
        started = true
        accountRepository.start()
        dayLogSync.start()
        observeAccount()
        observeLocalChanges()
    }

    suspend fun signIn(email: String, password: String) =
        accountRepository.signIn(email, password)

    suspend fun createAccount(email: String, password: String) =
        accountRepository.createAccount(email, password)

    suspend fun signInWithGoogle(activityContext: Context) =
        accountRepository.signInWithGoogle(activityContext)

    suspend fun sendPasswordReset(email: String) =
        accountRepository.sendPasswordReset(email)

    suspend fun signOut() = accountRepository.signOut()

    suspend fun deleteAccount() = accountRepository.deleteAccount()

    fun syncNow() {
        dayLogSync.syncNow()
        val uid = activeUid ?: return
        scope.launch { upload(uid) }
    }

    private fun observeAccount() {
        scope.launch {
            account.collectLatest { current ->
                remoteListener?.remove()
                remoteListener = null
                activeUid = current.userId
                lastAppliedRemoteMillis = current.userId?.let {
                    syncPreferences.getLong("last_remote_$it", 0L)
                } ?: 0L
                readyForLocalUploads = false
                when {
                    !current.configured -> _status.value = CloudSyncStatus.LocalOnly
                    current.userId == null -> _status.value = CloudSyncStatus.SignedOut
                    else -> initializeUser(current.userId)
                }
            }
        }
    }

    private val coreRevision = combine(
            sessionDao.observeAllForSync(),
            scheduledTaskDao.getAllFlow(),
            routineDao.observeAll(),
            routineDao.observeAllCompletions(),
            settingsDataStore.settingsFlow,
        ) { sessions, tasks, routines, completions, settings ->
            listOf(
                sessions.hashCode(), tasks.hashCode(), routines.hashCode(),
                completions.hashCode(), settings.cloudHashCode(),
            ).hashCode()
        }

    private val localRevision = combine(coreRevision, childProfileDao.observeAll(), scheduleAnchors.all, schedulePlans.all) { core, profiles, anchors, plans ->
        31 * (31 * (31 * core + profiles.hashCode()) + anchors.hashCode()) + plans.hashCode()
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    private fun observeLocalChanges() {
        scope.launch {
            localRevision
                .debounce(1_200L)
                .collect { revision ->
                    val uid = activeUid
                    if (uid != null && readyForLocalUploads && !applyingRemote &&
                        revision != lastSyncedLocalRevision
                    ) {
                        pendingUpload?.cancel()
                        pendingUpload = scope.launch { upload(uid) }
                    }
                }
        }
    }

    private suspend fun initializeUser(uid: String) {
        _status.value = CloudSyncStatus.Syncing
        val document = document(uid)
        try {
            val remote = document.get(Source.SERVER).await()
            if (remote.exists() && remote.long("updatedAtMillis") > lastAppliedRemoteMillis) {
                applyRemote(remote)
            } else {
                readyForLocalUploads = true
                upload(uid)
            }
            readyForLocalUploads = true
            listenForRemoteChanges(uid)
            if (_status.value is CloudSyncStatus.Syncing) {
                _status.value = CloudSyncStatus.Synced(System.currentTimeMillis())
            }
        } catch (error: Exception) {
            readyForLocalUploads = true
            listenForRemoteChanges(uid)
            _status.value = CloudSyncStatus.Error(error.userMessage())
        }
    }

    private fun listenForRemoteChanges(uid: String) {
        remoteListener = document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                _status.value = CloudSyncStatus.Error(error.userMessage())
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            if (!snapshot.exists()) {
                if (!snapshot.metadata.isFromCache && readyForLocalUploads) syncNow()
                return@addSnapshotListener
            }
            val updatedAt = snapshot.long("updatedAtMillis")
            val sourceDevice = snapshot.getString("deviceId")
            if (sourceDevice != deviceId && updatedAt > lastAppliedRemoteMillis) {
                scope.launch {
                    syncMutex.withLock { applyRemote(snapshot) }
                }
            }
        }
    }

    private suspend fun upload(uid: String) {
        syncMutex.withLock {
            if (uid != activeUid || !readyForLocalUploads) return
            _status.value = CloudSyncStatus.Syncing
            try {
                val now = System.currentTimeMillis()
                val settings = settingsDataStore.settingsFlow.first()
                val payload = hashMapOf<String, Any>(
                    "schemaVersion" to SCHEMA_VERSION,
                    "updatedAtMillis" to now,
                    "deviceId" to deviceId,
                    "sessions" to sessionDao.getAllForSync().map(SessionEntity::toCloudMap),
                    "scheduledTasks" to scheduledTaskDao.getAllForSync().map(ScheduledTaskEntity::toCloudMap),
                    "routines" to routineDao.getAllForSync().map(RoutineEntity::toCloudMap),
                    "routineCompletions" to routineDao.getAllCompletionsForSync().map(RoutineCompletionEntity::toCloudMap),
                    "childProfiles" to childProfileDao.getAllForSync().map(ChildProfileEntity::toCloudMap),
                    "settings" to (settings.toCloudMap() + mapOf("scheduleAnchors" to scheduleAnchors.all.first()
                        .mapValues { com.kidfocus.timer.data.schedule.ScheduleJson.anchorsMap(it.value) },
                        "schedulePlans" to schedulePlans.all.first().mapValues { (_, plan) -> plan?.let(com.kidfocus.timer.data.schedule.SchedulePlanJson::toMap) })),
                )
                document(uid).set(payload).await()
                lastAppliedRemoteMillis = now
                syncPreferences.edit().putLong("last_remote_$uid", now).apply()
                lastSyncedLocalRevision = localRevision.value
                _status.value = CloudSyncStatus.Synced(now)
            } catch (error: Exception) {
                _status.value = CloudSyncStatus.Error(error.userMessage())
            }
        }
    }

    private suspend fun applyRemote(snapshot: DocumentSnapshot) {
        if (snapshot.long("schemaVersion") != SCHEMA_VERSION.toLong()) return
        applyingRemote = true
        _status.value = CloudSyncStatus.Syncing
        try {
            val localSessions = sessionDao.getAllForSync().associateBy { it.id }
            val sessions = snapshot.mapList("sessions").mapNotNull(::sessionFromCloud).map { remote ->
                remote.copy(scheduledTaskId = localSessions[remote.id]?.scheduledTaskId)
            }
            val localTasks = scheduledTaskDao.getAllForSync().associateBy { it.id }
            val tasks = snapshot.mapList("scheduledTasks").mapNotNull(::taskFromCloud).map { remote ->
                remote.copy(photoUri = localTasks[remote.id]?.photoUri)
            }
            val localRoutines = routineDao.getAllForSync().associateBy { it.id }
            val routines = snapshot.mapList("routines").mapNotNull(::routineFromCloud).map { remote ->
                remote.copy(photoUri = localRoutines[remote.id]?.photoUri)
            }
            val profiles = snapshot.mapList("childProfiles").mapNotNull(::profileFromCloud)
            val routineIds = routines.mapTo(hashSetOf()) { it.id }
            val remoteCompletions = snapshot.mapList("routineCompletions")
                .mapNotNull(::completionFromCloud)
                .filter { it.routineId in routineIds }
            val localCompletions = routineDao.getAllCompletionsForSync()
            val completions = mergeRoutineCompletions(
                remote = remoteCompletions,
                local = localCompletions,
                validRoutineIds = routineIds,
            )

            database.withTransaction {
                routineDao.deleteAllCompletions()
                routineDao.deleteAllRoutines()
                scheduledTaskDao.deleteAll()
                sessionDao.deleteAllSessions()
                if (sessions.isNotEmpty()) sessionDao.insertSessions(sessions)
                if (tasks.isNotEmpty()) scheduledTaskDao.insertAll(tasks)
                if (routines.isNotEmpty()) routineDao.insertAll(routines)
                if (completions.isNotEmpty()) routineDao.insertAllCompletions(completions)
                if (profiles.isNotEmpty()) childProfileDao.upsertAll(profiles)
            }

            val cloudSettings = snapshot.get("settings") as? Map<*, *>
            if (cloudSettings != null) {
                scheduleAnchors.applyRemote(cloudSettings["scheduleAnchors"])
                schedulePlans.applyRemote(cloudSettings["schedulePlans"])
                val local = settingsDataStore.settingsFlow.first()
                settingsDataStore.saveSettings(
                    local.copy(
                        focusDurationMinutes = cloudSettings.int("focusDurationMinutes", local.focusDurationMinutes).coerceIn(5, 120),
                        breakDurationMinutes = cloudSettings.int("breakDurationMinutes", local.breakDurationMinutes).coerceIn(1, 30),
                        dailyGoalMinutes = cloudSettings.int("dailyGoalMinutes", local.dailyGoalMinutes).coerceIn(30, 240),
                        appTheme = AppTheme.fromKey(cloudSettings.string("appTheme", local.appTheme.name)),
                        soundEnabled = cloudSettings.bool("soundEnabled", local.soundEnabled),
                        vibrationEnabled = cloudSettings.bool("vibrationEnabled", local.vibrationEnabled),
                        learningAgeBand = cloudSettings.string("learningAgeBand", local.learningAgeBand)
                            .takeIf { it in setOf("2-3", "4-5", "l1", "l2", "l3") }
                            ?: local.learningAgeBand,
                        calmModeEnabled = cloudSettings.bool("calmModeEnabled", local.calmModeEnabled),
                        allowChildExtendFocus = cloudSettings.bool("allowChildExtendFocus", local.allowChildExtendFocus),
                        maxExtraFocusMinutes = cloudSettings.int("maxExtraFocusMinutes", local.maxExtraFocusMinutes)
                            .takeIf { it in com.kidfocus.timer.domain.model.FocusTimePolicy.extraChoices } ?: local.maxExtraFocusMinutes,
                    )
                )
            }

            localTasks.values.forEach { alarmScheduler.cancelTask(it.toDomain()) }
            alarmScheduler.scheduleAll(tasks.map { it.toDomain() })
            routines.forEach(routineAlarmScheduler::schedule)
            lastAppliedRemoteMillis = snapshot.long("updatedAtMillis")
            activeUid?.let { uid ->
                syncPreferences.edit().putLong("last_remote_$uid", lastAppliedRemoteMillis).apply()
            }
            lastSyncedLocalRevision = localRevision.value
            _status.value = CloudSyncStatus.Synced(System.currentTimeMillis())
        } catch (error: Exception) {
            _status.value = CloudSyncStatus.Error(error.userMessage())
        } finally {
            delay(300L)
            lastSyncedLocalRevision = localRevision.value
            applyingRemote = false
        }
    }

    private fun document(uid: String) = accountRepository.database()!!
        .collection("users").document(uid)
        .collection("backups").document("current")

    private fun Throwable.userMessage(): String = when {
        message?.contains("password", ignoreCase = true) == true -> "Email hoặc mật khẩu chưa đúng"
        message?.contains("network", ignoreCase = true) == true -> "Chưa có mạng; sẽ thử đồng bộ lại sau"
        else -> message ?: "Không thể đồng bộ dữ liệu"
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}

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

private fun ChildProfileEntity.toCloudMap() = mapOf(
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

private fun DocumentSnapshot.long(key: String) = (get(key) as? Number)?.toLong() ?: 0L

private fun DocumentSnapshot.mapList(key: String): List<Map<*, *>> =
    (get(key) as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()

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

private fun profileFromCloud(map: Map<*, *>): ChildProfileEntity? = runCatching {
    ChildProfileEntity(
        id = map.string("id").take(64),
        name = map.string("name").trim().take(40),
        avatarEmoji = map.string("avatarEmoji", "🐣").take(8),
        ageBand = map.string("ageBand", "4-5").takeIf {
            it in setOf("2-3", "4-5", "l1", "l2", "l3")
        } ?: "4-5",
        createdAtMillis = map.long("createdAtMillis", System.currentTimeMillis()),
        archived = map.bool("archived"),
    )
}.getOrNull()?.takeIf { it.id.isNotBlank() && it.name.isNotBlank() }

private fun completionFromCloud(map: Map<*, *>): RoutineCompletionEntity? = runCatching {
    RoutineCompletionEntity(
        id = map.long("id"), routineId = map.long("routineId"),
        occurrenceDate = map.string("occurrenceDate"),
        scheduledDeadlineMillis = map.long("scheduledDeadlineMillis"),
        completedAtMillis = map.long("completedAtMillis"), status = map.string("status"),
    )
}.getOrNull()?.takeIf { it.id > 0 && it.routineId > 0 && it.occurrenceDate.isNotBlank() }
