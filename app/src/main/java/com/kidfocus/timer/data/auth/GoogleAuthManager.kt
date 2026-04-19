package com.kidfocus.timer.data.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val GEMINI_SCOPE = "https://www.googleapis.com/auth/generative-language"
    }

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(GEMINI_SCOPE))
        .build()

    private val client = GoogleSignIn.getClient(context, gso)

    fun getSignInIntent(): Intent = client.signInIntent

    suspend fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> =
        withContext(Dispatchers.IO) {
            try {
                val account = Tasks.await(GoogleSignIn.getSignedInAccountFromIntent(data))
                Result.success(account)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getAccessToken(): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val androidAccount = account.account ?: return null
        return withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$GEMINI_SCOPE")
            } catch (e: UserRecoverableAuthException) {
                // Token consent revoked — force re-sign-in next call
                Tasks.await(client.signOut())
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    fun getSignedInEmail(): String? = GoogleSignIn.getLastSignedInAccount(context)?.email

    suspend fun signOut() = withContext(Dispatchers.IO) {
        Tasks.await(client.signOut())
    }
}
