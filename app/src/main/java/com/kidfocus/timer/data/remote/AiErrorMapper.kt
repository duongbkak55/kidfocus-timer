package com.kidfocus.timer.data.remote

import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException

enum class AiAuthError { SIGN_IN, APP_VERIFY }

/** Distinguishes a missing account from a callable rejected before its handler runs. */
object AiErrorMapper {
    fun auth(error: Throwable, isSignedIn: Boolean): AiAuthError? {
        if (error is AiAppVerificationException) return if (isSignedIn) AiAuthError.APP_VERIFY else AiAuthError.SIGN_IN
        val callable = error as? FirebaseFunctionsException ?: return null
        if (callable.code != FirebaseFunctionsException.Code.UNAUTHENTICATED) return null
        return if (!isSignedIn || callable.message?.contains("SIGN_IN_REQUIRED") == true) AiAuthError.SIGN_IN
            else AiAuthError.APP_VERIFY
    }
}

class AiAppVerificationException(cause: Throwable) : Exception("APP_VERIFY", cause)

/** The same immutable payload, including requestId, is sent on both attempts. */
internal suspend fun <T> retryAiCall(
    payload: Map<String, Any>,
    isSignedIn: () -> Boolean,
    refreshCredentials: suspend () -> Unit,
    send: suspend (Map<String, Any>) -> T,
): T {
    try {
        return send(payload)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        if (AiErrorMapper.auth(error, isSignedIn()) != AiAuthError.APP_VERIFY) throw error
        try {
            refreshCredentials()
            return send(payload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (retryError: Exception) {
            if (AiErrorMapper.auth(retryError, isSignedIn()) == AiAuthError.SIGN_IN) throw retryError
            throw AiAppVerificationException(retryError)
        }
    }
}
