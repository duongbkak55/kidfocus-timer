package com.kidfocus.timer.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.kidfocus.timer.data.database.LearningAttemptDao
import com.kidfocus.timer.data.database.LearningAttemptEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

data class LearningResult(
    val gameId: String,
    val ageBand: String,
    val score: Int?,
    val totalQuestions: Int?,
    val durationMillis: Long,
    val completed: Boolean,
)

data class LearningProgress(
    val gameId: String,
    val attempts: Int,
    val completedAttempts: Int,
    val correctAnswers: Int,
    val totalQuestions: Int,
    val bestPercent: Int?,
    val totalDurationMillis: Long,
    val lastPlayedAtMillis: Long,
)

/**
 * Offline-first learning history. Each attempt is a separate Firestore document, avoiding the
 * 1 MiB limit of the existing compact backup document and making retry writes idempotent.
 */
@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LearningRepository @Inject constructor(
    private val dao: LearningAttemptDao,
    private val accountRepository: FirebaseAccountRepository,
    private val childProfileRepository: ChildProfileRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uploadMutex = Mutex()
    private var started = false
    private var listener: ListenerRegistration? = null

    val attempts: Flow<List<LearningAttemptEntity>> = combine(
        accountRepository.account.map { it.userId }.distinctUntilChanged(),
        childProfileRepository.activeProfileId,
    ) { ownerUid, profileId -> ownerUid to profileId }
        .flatMapLatest { (ownerUid, profileId) -> dao.observeForOwnerAndProfile(ownerUid, profileId) }

    val progress: Flow<List<LearningProgress>> = attempts.map(::summarizeLearningAttempts)

    fun start() {
        if (started) return
        started = true
        scope.launch {
            accountRepository.account
                .map { it.userId }
                .distinctUntilChanged()
                .collectLatest { uid ->
                    listener?.remove()
                    listener = null
                    if (uid != null) connect(uid)
                }
        }
    }

    suspend fun record(result: LearningResult) {
        val uid = accountRepository.account.value.userId
        val total = result.totalQuestions?.coerceIn(1, 200)
        val score = result.score?.coerceIn(0, total ?: 200)
        dao.upsert(
            LearningAttemptEntity(
                id = UUID.randomUUID().toString(),
                ownerUid = uid,
                gameId = result.gameId.trim().take(48).ifBlank { "unknown" },
                ageBand = result.ageBand.takeIf(VALID_AGE_BANDS::contains) ?: "4-5",
                score = score,
                totalQuestions = total,
                durationMillis = result.durationMillis.coerceIn(0L, MAX_DURATION_MILLIS),
                completed = result.completed,
                createdAtMillis = System.currentTimeMillis(),
                synced = false,
                childProfileId = childProfileRepository.currentProfileId(),
            ),
        )
        if (uid != null) uploadPending(uid)
    }

    fun syncNow() {
        val uid = accountRepository.account.value.userId ?: return
        scope.launch { uploadPending(uid) }
    }

    private suspend fun connect(uid: String) {
        dao.claimOfflineAttempts(uid)
        try {
            uploadPending(uid)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Room remains authoritative; a later result or manual sync retries this batch.
        }
        val database = accountRepository.database() ?: return
        listener = database.collection("users").document(uid)
            .collection(COLLECTION)
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(MAX_REMOTE_ATTEMPTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val rows = snapshot.documents.mapNotNull { it.toLearningAttempt(uid) }
                if (rows.isNotEmpty()) scope.launch { dao.upsertAll(rows) }
            }
    }

    private suspend fun uploadPending(uid: String) = uploadMutex.withLock {
        if (accountRepository.account.value.userId != uid) return
        val database = accountRepository.database() ?: return
        while (true) {
            val pending = dao.pendingForOwner(uid)
            if (pending.isEmpty()) break
            val batch = database.batch()
            val collection = database.collection("users").document(uid).collection(COLLECTION)
            pending.forEach { attempt ->
                batch.set(collection.document(attempt.id), attempt.toCloudMap())
            }
            batch.commit().await()
            dao.markSynced(pending.map { it.id })
            if (pending.size < 400) break
        }
    }

    private companion object {
        const val COLLECTION = "learningAttempts"
        const val MAX_REMOTE_ATTEMPTS = 5_000L
        const val MAX_DURATION_MILLIS = 4 * 60 * 60 * 1_000L
        val VALID_AGE_BANDS = setOf("2-3", "4-5", "l1", "l2", "l3")
    }
}

internal fun summarizeLearningAttempts(rows: List<LearningAttemptEntity>): List<LearningProgress> =
    rows.groupBy { it.gameId }.map { (gameId, gameRows) ->
        val scored = gameRows.filter {
            it.score != null && it.totalQuestions != null && it.totalQuestions > 0
        }
        LearningProgress(
            gameId = gameId,
            attempts = gameRows.size,
            completedAttempts = gameRows.count { it.completed },
            correctAnswers = scored.sumOf { it.score ?: 0 },
            totalQuestions = scored.sumOf { it.totalQuestions ?: 0 },
            bestPercent = scored.maxOfOrNull {
                (((it.score ?: 0) * 100f) / (it.totalQuestions ?: 1)).toInt()
            },
            totalDurationMillis = gameRows.sumOf { it.durationMillis },
            lastPlayedAtMillis = gameRows.maxOf { it.createdAtMillis },
        )
    }.sortedByDescending { it.lastPlayedAtMillis }

private fun LearningAttemptEntity.toCloudMap(): Map<String, Any> = buildMap {
    put("gameId", gameId)
    put("ageBand", ageBand)
    score?.let { put("score", it) }
    totalQuestions?.let { put("totalQuestions", it) }
    put("durationMillis", durationMillis)
    put("completed", completed)
    put("createdAtMillis", createdAtMillis)
    put("childProfileId", childProfileId)
}

private fun DocumentSnapshot.toLearningAttempt(ownerUid: String): LearningAttemptEntity? = runCatching {
    val gameId = getString("gameId").orEmpty().take(48)
    val total = (get("totalQuestions") as? Number)?.toInt()?.coerceIn(1, 200)
    LearningAttemptEntity(
        id = id,
        ownerUid = ownerUid,
        gameId = gameId,
        ageBand = getString("ageBand").orEmpty().takeIf {
            it in setOf("2-3", "4-5", "l1", "l2", "l3")
        } ?: "4-5",
        score = (get("score") as? Number)?.toInt()?.coerceIn(0, total ?: 200),
        totalQuestions = total,
        durationMillis = (get("durationMillis") as? Number)?.toLong()
            ?.coerceIn(0L, 4 * 60 * 60 * 1_000L) ?: 0L,
        completed = getBoolean("completed") == true,
        createdAtMillis = (get("createdAtMillis") as? Number)?.toLong() ?: 0L,
        synced = true,
        childProfileId = getString("childProfileId") ?: "default",
    )
}.getOrNull()?.takeIf { it.gameId.isNotBlank() && it.createdAtMillis > 0L }
