package com.kidfocus.timer.data.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomOwnerGateTest {
    @Test fun accountBDoesNotScanOrUploadAccountARows() = runBlocking {
        var owner: String? = "A"
        val localRows = mutableListOf("A task", "A routine", "A session", "A child")
        val uploaded = mutableListOf<String>()
        val events = mutableListOf<String>()
        val gate = RoomOwnerGate(
            currentOwner = { owner },
            saveOwner = { owner = it; events += "owner:$it" },
            clearLocal = { old, new -> events += "clear:$old->$new"; localRows.clear() },
            hasGuestData = { true },
            askGuestMerge = { error("not guest") },
        )
        assertTrue(gate.prepare("B"))
        uploaded += localRows
        assertEquals(emptyList<String>(), uploaded)
        assertEquals(listOf("clear:A->B", "owner:B"), events)
    }

    @Test fun guestMergeKeepsRowsAndClaimsOwnerOnce() = runBlocking {
        var owner: String? = null
        var asks = 0
        var clears = 0
        val gate = RoomOwnerGate(
            currentOwner = { owner }, saveOwner = { owner = it },
            clearLocal = { _, _ -> clears++ }, askGuestMerge = { asks++; true },
            hasGuestData = { true },
        )
        assertFalse(gate.prepare("A"))
        assertFalse(gate.prepare("A"))
        assertEquals("A", owner)
        assertEquals(1, asks)
        assertEquals(0, clears)
    }

    @Test fun guestDiscardClearsBeforeClaimingOwner() = runBlocking {
        var owner: String? = null
        val events = mutableListOf<String>()
        val gate = RoomOwnerGate(
            currentOwner = { owner },
            saveOwner = { owner = it; events += "owner" },
            clearLocal = { _, _ -> events += "clear" },
            hasGuestData = { true },
            askGuestMerge = { false },
        )
        assertTrue(gate.prepare("A"))
        assertEquals(listOf("clear", "owner"), events)
    }
    @Test fun emptyInstallClaimsOwnerWithoutPrompt() = runBlocking {
        var owner: String? = null
        val gate = RoomOwnerGate({ owner }, { owner = it }, { _, _ -> error("clear") },
            { false }, { error("prompt") })
        assertFalse(gate.prepare("A"))
        assertEquals("A", owner)
    }
}
