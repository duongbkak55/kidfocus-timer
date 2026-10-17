package com.kidfocus.timer.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationLeasePolicyTest {
    @Test fun onlyOneLiveLeaseAndNewerEditSurvivesSecondMigrator() {
        val first = MigrationLeasePolicy.claim(1000, "phone")
        assertEquals(MigrationLeasePolicy.Decision.Wait(121000),
            MigrationLeasePolicy.decide(first, 2000, "web"))
        assertEquals(MigrationLeasePolicy.Decision.Claim,
            MigrationLeasePolicy.decide(first, 121001, "web"))
        val original = SyncV2Model.toDoc("tasks", mapOf("id" to 7L, "name" to "Old",
            "daysOfWeek" to "2"), 1000, "migration")!!
        val edited = SyncV2Model.toDoc("tasks", mapOf("id" to 7L, "name" to "Edited",
            "daysOfWeek" to "2"), 2000, "parent")!!
        var current = edited
        repeat(2) {
            val candidate = SyncV2Model.fromDoc("tasks", original.id, original.data)!!
            val remote = SyncV2Model.fromDoc("tasks", current.id, current.data)!!
            if (SyncV2Model.pickWinner("tasks", candidate, remote) == "local") current = original
        }
        assertEquals("Edited", current.data["name"])
        assertTrue(MigrationLeasePolicy.decide(mapOf("schemaVersion" to 2,
            "migratedAtMillis" to 1000L), 122000, "web") is MigrationLeasePolicy.Decision.Ready)
    }
}
