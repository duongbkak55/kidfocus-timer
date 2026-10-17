package com.kidfocus.timer.data.cloud

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotBatchTest {
    @Test fun initialSnapshotsScanOnceEachForTwoThousandSessionsAndTwoHundredTasks() = runTest {
        var scans = 0
        var receives = 0
        val sessions = (1..2000).toList()
        val tasks = (1..200).toList()
        for (changes in listOf(sessions, tasks)) {
            SnapshotBatch.apply(changes, { scans++ }, { it % 2 == 0 }, { receives++ })
        }
        assertEquals(2, scans)
        assertEquals(1100, receives)
    }
}
