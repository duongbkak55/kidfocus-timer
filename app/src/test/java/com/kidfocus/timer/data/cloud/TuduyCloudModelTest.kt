package com.kidfocus.timer.data.cloud

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TuduyCloudModelTest {
    @Test fun webStoreRoundTripAndWinner() {
        val local = TuduyCloudModel.toDoc(
            """{"stats":{"sudoku":{"level":3,"played":2}},"review":[{"id":1}]}""",
            2000L, "android",
        )!!
        val remote = local + mapOf("updatedAtMillis" to 1000L, "deviceId" to "web")
        assertEquals("local", TuduyCloudModel.pickWinner(local, remote))
        val parsed = JSONObject(TuduyCloudModel.storeJson(local)!!)
        assertEquals(3, parsed.getJSONObject("stats").getJSONObject("sudoku").getInt("level"))
        assertEquals(1, parsed.getJSONArray("review").length())
    }

    @Test fun invalidAndOversizedStoresAreNotUploaded() {
        assertNull(TuduyCloudModel.toDoc("""{"stats":[],"review":[]}""", 2000L, "android"))
        assertNull(TuduyCloudModel.toDoc("""{"stats":{},"review":[]}""", 0L, "android"))
        val oversized = """{"stats":{},"review":[${List(13) { "{}" }.joinToString(",")}]}"""
        assertNull(TuduyCloudModel.toDoc(oversized, 2000L, "android"))
        assertTrue(TuduyCloudModel.validProfileId("be-na"))
        assertTrue(!TuduyCloudModel.validProfileId("bad/name"))
    }
}
