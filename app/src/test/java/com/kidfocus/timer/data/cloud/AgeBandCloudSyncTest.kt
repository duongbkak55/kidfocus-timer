package com.kidfocus.timer.data.cloud

import com.kidfocus.timer.data.database.ChildProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AgeBandCloudSyncTest {
    @Test fun `profile cloud payload round trips five to six`() {
        val profile = ChildProfileEntity.default().copy(name = "Bé Na", ageBand = "5-6")
        assertEquals("5-6", profile.toCloudMap()["ageBand"])
        assertEquals(profile, profileFromCloud(profile.toCloudMap()))
    }

    @Test fun `unknown cloud band retains previous fallback`() {
        val map = ChildProfileEntity.default().toCloudMap() + ("ageBand" to "unknown")
        assertEquals("4-5", profileFromCloud(map)?.ageBand)
    }
}
