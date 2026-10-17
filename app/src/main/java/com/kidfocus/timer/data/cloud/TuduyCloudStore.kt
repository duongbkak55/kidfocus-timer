package com.kidfocus.timer.data.cloud

import android.content.Context
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/** Only the Tu Duy {stats, review} store is shared; other Learning Hub state stays app-owned. */
@Singleton
class TuduyCloudStore @Inject constructor(
    @ApplicationContext context: Context,
    private val account: FirebaseAccountRepository,
) {
    private val deviceId = context.getSharedPreferences("tuduy_cloud", Context.MODE_PRIVATE).let { prefs ->
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    fun observe(profileId: String, onStore: (String) -> Unit): ListenerRegistration? {
        val uid = account.account.value.userId ?: return null
        val db = account.database() ?: return null
        if (!TuduyCloudModel.validProfileId(profileId)) return null
        return db.collection("users").document(uid).collection("learning")
            .document("${profileId}_tuduy").addSnapshotListener { snapshot, error ->
                if (error == null && account.account.value.userId == uid) {
                    TuduyCloudModel.storeJson(snapshot?.data)?.let(onStore)
                }
            }
    }

    suspend fun load(profileId: String): String? {
        val uid = account.account.value.userId ?: return null
        val db = account.database() ?: return null
        if (!TuduyCloudModel.validProfileId(profileId)) return null
        val data = db.collection("users").document(uid).collection("learning")
            .document("${profileId}_tuduy").get(Source.SERVER).await().data
        return TuduyCloudModel.storeJson(data)
    }

    suspend fun save(profileId: String, rawStore: String): String? {
        val uid = account.account.value.userId ?: return null
        val db = account.database() ?: return null
        if (!TuduyCloudModel.validProfileId(profileId)) return null
        val candidate = TuduyCloudModel.toDoc(rawStore, System.currentTimeMillis(), deviceId) ?: return null
        val ref = db.collection("users").document(uid).collection("learning").document("${profileId}_tuduy")
        val winner = db.runTransaction { tx ->
            val remote = tx.get(ref).data
            if (TuduyCloudModel.pickWinner(candidate, remote) == "local") {
                tx.set(ref, candidate)
                candidate
            } else remote
        }.await()
        return TuduyCloudModel.storeJson(winner)
    }
}

/** Pure wire model matching the web W2 adapter; never includes child email or raw attempts. */
object TuduyCloudModel {
    fun validProfileId(id: String) = id.isNotBlank() && id.length <= 64 && '/' !in id

    fun toDoc(rawStore: String, nowMillis: Long, deviceId: String): Map<String, Any>? = runCatching {
        require(rawStore.length <= 131_072 && nowMillis > 0 && deviceId.isNotBlank())
        val root = JSONObject(rawStore)
        val stats = root.getJSONObject("stats")
        val review = root.getJSONArray("review")
        require(review.length() <= 12)
        mapOf(
            "stats" to stats.toPlainMap(), "review" to review.toPlainList(),
            "updatedAtMillis" to nowMillis, "deviceId" to deviceId, "schemaVersion" to 2,
        )
    }.getOrNull()

    fun pickWinner(local: Map<String, Any>?, remote: Map<String, Any>?): String {
        if (local == null) return "remote"
        if (remote == null) return "local"
        val localAt = (local["updatedAtMillis"] as? Number)?.toLong() ?: 0L
        val remoteAt = (remote["updatedAtMillis"] as? Number)?.toLong() ?: 0L
        if (localAt != remoteAt) return if (localAt > remoteAt) "local" else "remote"
        return if ((local["deviceId"] as? String).orEmpty() > (remote["deviceId"] as? String).orEmpty())
            "local" else "remote"
    }

    fun storeJson(data: Map<String, Any>?): String? {
        if (data == null) return JSONObject().put("stats", JSONObject()).put("review", JSONArray()).toString()
        if ((data["schemaVersion"] as? Number)?.toInt() != 2 || data["stats"] !is Map<*, *> ||
            data["review"] !is List<*>) return null
        return JSONObject().put("stats", JSONObject(data["stats"] as Map<*, *>))
            .put("review", JSONArray(data["review"] as List<*>)).toString()
    }

    private fun JSONObject.toPlainMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
        plain(get(key))
    }
    private fun JSONArray.toPlainList(): List<Any?> = (0 until length()).map { plain(get(it)) }
    private fun plain(value: Any?): Any? = when (value) {
        is JSONObject -> value.toPlainMap()
        is JSONArray -> value.toPlainList()
        JSONObject.NULL -> null
        else -> value
    }
}
