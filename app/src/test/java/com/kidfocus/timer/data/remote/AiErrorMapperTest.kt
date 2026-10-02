package com.kidfocus.timer.data.remote

import android.app.Application
import com.google.firebase.functions.FirebaseFunctionsException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AiErrorMapperTest {
    private fun callable(code: FirebaseFunctionsException.Code, message: String): FirebaseFunctionsException =
        mockk<FirebaseFunctionsException>().also {
            every { it.code } returns code
            every { it.message } returns message
        }

    @Test fun `only explicit sign-in or a signed-out account shows sign-in`() {
        assertEquals(AiAuthError.SIGN_IN, AiErrorMapper.auth(callable(FirebaseFunctionsException.Code.UNAUTHENTICATED, "SIGN_IN_REQUIRED"), true))
        assertEquals(AiAuthError.SIGN_IN, AiErrorMapper.auth(callable(FirebaseFunctionsException.Code.UNAUTHENTICATED, "Unauthenticated"), false))
        assertEquals(AiAuthError.APP_VERIFY, AiErrorMapper.auth(callable(FirebaseFunctionsException.Code.UNAUTHENTICATED, "Unauthenticated"), true))
        assertEquals(AiAuthError.APP_VERIFY, AiErrorMapper.auth(AiAppVerificationException(Exception()), true))
        assertEquals(AiAuthError.SIGN_IN, AiErrorMapper.auth(AiAppVerificationException(Exception()), false))
        assertNull(AiErrorMapper.auth(callable(FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED, "quota"), true))
    }

    @Test fun `signed-in App Check failure refreshes then retries the same requestId once`() = runTest {
        val request = mapOf<String, Any>("requestId" to "fixed-request", "mode" to "LOG")
        val seen = mutableListOf<Map<String, Any>>()
        var refreshes = 0
        val result = retryAiCall(request, { true }, { refreshes++ }) { payload ->
            seen += payload
            if (seen.size == 1) throw callable(FirebaseFunctionsException.Code.UNAUTHENTICATED, "Unauthenticated")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, refreshes)
        assertEquals(2, seen.size)
        assertSame(seen[0], seen[1])
        assertEquals("fixed-request", seen[1]["requestId"])
    }

    @Test fun `failed retry becomes app verification error without a third request`() = runTest {
        var sends = 0
        var refreshes = 0
        try {
            retryAiCall(mapOf("requestId" to "one"), { true }, { refreshes++ }) {
                sends++
                if (sends <= 2) throw callable(FirebaseFunctionsException.Code.UNAUTHENTICATED, "Unauthenticated")
                "unexpected"
            }
            fail("Expected App verification error")
        } catch (error: AiAppVerificationException) {
            assertEquals(AiAuthError.APP_VERIFY, AiErrorMapper.auth(error, true))
        }
        assertEquals(2, sends)
        assertEquals(1, refreshes)
    }

    @Test fun `explicit sign-in required never refreshes or retries`() = runTest {
        var sends = 0
        var refreshes = 0
        try {
            retryAiCall(mapOf("requestId" to "one"), { true }, { refreshes++ }) {
                sends++
                if (sends == 1) throw callable(FirebaseFunctionsException.Code.UNAUTHENTICATED, "SIGN_IN_REQUIRED")
                "unexpected"
            }
            fail("Expected sign-in error")
        } catch (error: FirebaseFunctionsException) {
            assertEquals(AiAuthError.SIGN_IN, AiErrorMapper.auth(error, true))
        }
        assertEquals(1, sends)
        assertEquals(0, refreshes)
    }

    @Test fun `credential refresh failure becomes app verification error without retry`() = runTest {
        var sends = 0
        try {
            retryAiCall(mapOf("requestId" to "one"), { true }, { throw IllegalStateException("refresh failed") }) {
                sends++
                if (sends == 1) throw callable(FirebaseFunctionsException.Code.UNAUTHENTICATED, "Unauthenticated")
                "unexpected"
            }
            fail("Expected App verification error")
        } catch (error: AiAppVerificationException) {
            assertEquals(AiAuthError.APP_VERIFY, AiErrorMapper.auth(error, true))
        }
        assertEquals(1, sends)
    }
}
