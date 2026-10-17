package com.kidfocus.timer.data.cloud

import org.json.JSONArray
import org.json.JSONObject

/** Firestore primitives only; keys are sorted for stable local-change detection. */
internal object SyncV2Json {
    fun encode(value: Map<String, Any?>): String = canonical(value)

    @Suppress("UNCHECKED_CAST")
    fun decode(value: String): Map<String, Any?> = read(JSONObject(value)) as Map<String, Any?>

    fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is Map<*, *> -> value.entries.sortedBy { it.key.toString() }.joinToString(",", "{", "}") {
            JSONObject.quote(it.key.toString()) + ":" + canonical(it.value)
        }
        is Iterable<*> -> value.joinToString(",", "[", "]") { canonical(it) }
        is Array<*> -> value.joinToString(",", "[", "]") { canonical(it) }
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun read(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { read(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { read(value.get(it)) }
        else -> value
    }
}
