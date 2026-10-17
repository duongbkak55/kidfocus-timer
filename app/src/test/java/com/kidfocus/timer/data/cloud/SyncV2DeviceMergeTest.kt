package com.kidfocus.timer.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic stand-in for a Firestore transaction and two offline Room queues. */
class SyncV2DeviceMergeTest {
    private class Cloud {
        val docs = mutableMapOf<String, Map<String, Any?>>()
        fun transact(collection: String, doc: SyncV2Model.Doc): Map<String, Any?> {
            val key = "$collection/${doc.id}"
            val current = docs[key]?.let { SyncV2Model.fromDoc(collection, doc.id, it) }
            val candidate = SyncV2Model.fromDoc(collection, doc.id, doc.data)!!
            if (current == null || SyncV2Model.pickWinner(collection, candidate, current) == "local")
                docs[key] = doc.data
            return docs[key]!!
        }
    }
    private class Device(val id: String) {
        val queue = mutableListOf<SyncV2Model.Doc>()
        fun task(taskId: Long, name: String, at: Long, deleted: Boolean = false) {
            queue += SyncV2Model.toDoc("tasks", mapOf("id" to taskId, "taskType" to "CUSTOM",
                "name" to name, "daysOfWeek" to "2,3"), at, id, deleted)!!
        }
        fun reconnect(cloud: Cloud) {
            queue.forEach { cloud.transact("tasks", it) }
            queue.clear()
        }
    }

    @Test fun independentOfflineEditsMergeAndSameItemUsesTimestampThenDeviceId() {
        val cloud = Cloud(); val phone = Device("phone"); val web = Device("web")
        phone.task(1, "Đọc sách", 100)
        web.task(2, "Vẽ", 100)
        web.reconnect(cloud); phone.reconnect(cloud)
        assertEquals(setOf("tasks/1", "tasks/2"), cloud.docs.keys)

        phone.task(1, "Đọc 10 phút", 200)
        web.task(1, "Đọc 15 phút", 300)
        web.reconnect(cloud); phone.reconnect(cloud)
        assertEquals("Đọc 15 phút", cloud.docs["tasks/1"]!!["name"])

        phone.task(1, "Đọc 20 phút", 400)
        web.task(1, "Đọc 25 phút", 400)
        phone.reconnect(cloud); web.reconnect(cloud)
        assertEquals("Đọc 25 phút", cloud.docs["tasks/1"]!!["name"])
    }

    @Test fun offlineDeletionIsATombstoneAndStaleUploadCannotResurrectIt() {
        val cloud = Cloud(); val phone = Device("phone"); val web = Device("web")
        phone.task(7, "Câu đố", 100); phone.reconnect(cloud)
        web.task(7, "Sửa cũ", 150) // disconnected before the delete
        phone.task(7, "Câu đố", 200, deleted = true)
        phone.reconnect(cloud); web.reconnect(cloud)
        assertTrue(cloud.docs["tasks/7"]!!["deleted"] as Boolean)
        assertEquals(200L, (cloud.docs["tasks/7"]!!["updatedAtMillis"] as Number).toLong())
        assertFalse(cloud.docs["tasks/7"]!!.containsKey("photoUri"))
    }
}
