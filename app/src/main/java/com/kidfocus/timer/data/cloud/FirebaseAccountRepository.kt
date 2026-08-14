package com.kidfocus.timer.data.cloud

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.kidfocus.timer.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class CloudAccount(
    val configured: Boolean,
    val userId: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val providerLabel: String? = null,
) {
    val isSignedIn: Boolean get() = userId != null
}

/** Optional Firebase entry point. The rest of the app remains local-only without config. */
@Singleton
class FirebaseAccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val configured = listOf(
        BuildConfig.FIREBASE_API_KEY,
        BuildConfig.FIREBASE_APP_ID,
        BuildConfig.FIREBASE_PROJECT_ID,
    ).all(String::isNotBlank)

    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private val _account = MutableStateFlow(CloudAccount(configured = configured))
    val account: StateFlow<CloudAccount> = _account.asStateFlow()

    fun start() {
        if (!configured || auth != null) return
        val existing = FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
        val app = existing ?: FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build(),
            APP_NAME,
        )

        FirebaseAppCheck.getInstance(app)
            .installAppCheckProviderFactory(BuildAppCheckProvider.factory())

        auth = FirebaseAuth.getInstance(app).also { firebaseAuth ->
            firebaseAuth.addAuthStateListener { current ->
                val user = current.currentUser
                val providers = user?.providerData?.mapNotNull { it.providerId }.orEmpty()
                _account.value = CloudAccount(
                    configured = true,
                    userId = user?.uid,
                    email = user?.email,
                    displayName = user?.displayName
                        ?: user?.providerData?.firstNotNullOfOrNull { it.displayName },
                    photoUrl = user?.photoUrl?.toString()
                        ?: user?.providerData?.firstNotNullOfOrNull { it.photoUrl?.toString() },
                    providerLabel = when {
                        "google.com" in providers -> "Google"
                        "facebook.com" in providers -> "Facebook"
                        "password" in providers -> "Email & mật khẩu"
                        user != null -> "Firebase"
                        else -> null
                    },
                )
            }
        }
        firestore = FirebaseFirestore.getInstance(app)
    }

    fun database(): FirebaseFirestore? = firestore

    fun firebaseApp(): FirebaseApp? = auth?.app

    suspend fun signIn(email: String, password: String) {
        requireConfigured()
        auth!!.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun createAccount(email: String, password: String) {
        requireConfigured()
        auth!!.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signInWithGoogle(activityContext: Context) {
        requireConfigured()
        check(BuildConfig.FIREBASE_WEB_CLIENT_ID.isNotBlank()) {
            "Google Sign-In chưa được cấu hình"
        }
        val googleIdOption = GetSignInWithGoogleOption.Builder(
            serverClientId = BuildConfig.FIREBASE_WEB_CLIENT_ID,
        )
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)
        val customCredential = result.credential as? CustomCredential
            ?: error("Không nhận được tài khoản Google")
        check(customCredential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Không nhận được tài khoản Google"
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(customCredential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        auth!!.signInWithCredential(firebaseCredential).await()
    }

    suspend fun sendPasswordReset(email: String) {
        requireConfigured()
        auth!!.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun signOut() {
        auth?.signOut()
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    suspend fun deleteAccount() {
        requireConfigured()
        check(auth?.currentUser != null) { "Hãy đăng nhập trước" }
        FirebaseFunctions.getInstance(auth!!.app, "asia-southeast1")
            .getHttpsCallable("deleteAccount")
            .call()
            .await()
        auth?.signOut()
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private fun requireConfigured() {
        check(configured && auth != null) { "Firebase chưa được cấu hình" }
    }

    private companion object {
        const val APP_NAME = "KidFocusCloud"
    }
}
