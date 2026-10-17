package com.kidfocus.timer.data.cloud

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SyncV2ModelTest {
    private fun convert(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { convert(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { convert(value.get(it)) }
        else -> value
    }
    private fun fixture(name: String): Any = javaClass.classLoader!!.getResourceAsStream("sync/$name")!!.use {
        val json = it.reader().readText()
        convert(if (json.trimStart().startsWith('[')) JSONArray(json) else JSONObject(json))!!
    }
    @Suppress("UNCHECKED_CAST")
    private fun obj(value: Any?) = value as Map<String, Any?>
    private fun n(value: Any?) = (value as Number).toLong()

    @Test fun migrationMatchesSharedFixtures() {
        val migrated = SyncV2Model.migrateV1(obj(fixture("v1-snapshot.json")), 5, "android-x")
        val expected = obj(fixture("v1-expected.json"))
        val by = migrated.docs.groupBy { it.collection }.mapValues { (_, docs) -> docs.associate { it.id to it.data } }
        assertEquals(n(expected["docCount"]).toInt(), migrated.docs.size)
        assertEquals(expected["profiles"], by["profiles"]!!.keys.sorted())
        obj(expected["profileAgeBands"]).forEach { (id, band) -> assertEquals(band, by["profiles"]!![id]!!["ageBand"]) }
        for (collection in listOf("tasks", "routines", "completions", "sessions")) {
            val rows = obj(expected[collection]); val actual = by[collection]!!
            assertEquals(rows.keys, actual.keys)
            rows.forEach { (id, fields) -> obj(fields).forEach { (key, value) ->
                val found = actual[id]!![key]
                if (value is Number) assertEquals("$collection/$id.$key", n(value), n(found))
                else assertEquals("$collection/$id.$key", value, found)
            } }
        }
        val settings = migrated.settings!!; val expectedSettings = obj(expected["settings"])
        assertEquals(n(expectedSettings["dailyGoalMinutes"]), n(settings["dailyGoalMinutes"]))
        assertEquals(expectedSettings["learningAgeBand"], settings["learningAgeBand"])
        assertTrue(settings["scheduleAnchors"] is Map<*, *>)
        assertTrue((settings["schedulePlans"] as Map<*, *>).containsKey("default"))
        migrated.docs.forEach { doc ->
            assertEquals(2L, n(doc.data["schemaVersion"]))
            assertEquals(false, doc.data["deleted"])
            assertEquals(1792000000000L, n(doc.data["updatedAtMillis"]))
            assertEquals(doc.id, SyncV2Model.fromDoc(doc.collection, doc.id, doc.data)?.id)
        }
    }

    @Test fun winnerMatchesSharedConflictFixtureInBothDirections() {
        @Suppress("UNCHECKED_CAST")
        val cases = fixture("conflicts.json") as List<Map<String, Any?>>
        for (case in cases) {
            fun parsed(raw: Any?): SyncV2Model.Parsed? = raw?.let { obj(it) }?.let { row ->
                SyncV2Model.Parsed("fixture", mapOf(
                    "status" to (row["status"] ?: "ON_TIME"),
                    "completedAtMillis" to (row["completedAtMillis"] ?: 0),
                ), n(row["updatedAtMillis"]), row["deviceId"] as String, row["deleted"] == true)
            }
            val local = parsed(case["local"]); val remote = parsed(case["remote"])
            val collection = case["collection"] as String; val winner = case["winner"] as String
            assertEquals(case["name"] as String, winner, SyncV2Model.pickWinner(collection, local, remote))
            if (local != null && remote != null) {
                assertEquals(case["name"] as String, if (winner == "local") "remote" else "local",
                    SyncV2Model.pickWinner(collection, remote, local))
            }
        }
    }

    @Test fun invalidDocumentsAreIgnoredAndIdsAreRandomSafeIntegers() {
        val task = SyncV2Model.toDoc("tasks", mapOf("id" to 7, "name" to "Vẽ", "daysOfWeek" to "2"), 1, "a")!!
        assertNotNull(SyncV2Model.fromDoc("tasks", "7", task.data))
        assertNull(SyncV2Model.fromDoc("tasks", "8", task.data))
        assertNull(SyncV2Model.fromDoc("tasks", "7", task.data + ("schemaVersion" to 1)))
        assertNull(SyncV2Model.toDoc("profiles", mapOf("id" to "a/b", "name" to "Bé"), 1, "a"))
        val ids = (0 until 100_000).map { SyncV2Model.newId() }
        assertTrue(ids.all { it in 1..(1L shl 52) })
        assertEquals(ids.size, ids.toSet().size)
        assertFalse(task.data.containsKey("photoUri"))
    }
}
