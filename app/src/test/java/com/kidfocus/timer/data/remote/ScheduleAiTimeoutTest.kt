package com.kidfocus.timer.data.remote

import com.google.firebase.functions.HttpsCallableReference
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ScheduleAiTimeoutTest {
    @Test fun callableUsesNinetyFiveSecondNetworkTimeout() {
        val callable = mockk<HttpsCallableReference>(relaxed = true)
        assertSame(callable, callable.withAiScheduleTimeout())
        verify(exactly = 1) { callable.setTimeout(95, TimeUnit.SECONDS) }
    }

    @Test fun coroutineAllowsResponseBeforeNinetyFiveSeconds() = runTest {
        assertEquals("ok", withAiScheduleTimeout { delay(94_999); "ok" })
    }

    @Test fun coroutineCancelsAtNinetyFiveSeconds() = runTest {
        var timedOut = false
        try {
            withAiScheduleTimeout { delay(95_001) }
        } catch (_: TimeoutCancellationException) {
            timedOut = true
        }
        assertEquals(true, timedOut)
        assertEquals(95_000L, AI_SCHEDULE_TIMEOUT_MILLIS)
    }
}
