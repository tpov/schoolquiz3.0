package com.tpov.schoolquiz.platform.firebase.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.tpov.schoolquiz.shared.core.outbox.AccountSwitchGuard
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountChooserHost
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.GoogleLinkOutcome
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.PlatformAccountChooserHost
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.GoogleSignInRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Attaches a Google identity to the account that is already signed in.
 *
 * Link first, sign in second. The game starts anonymous and a player may have spent weeks on that
 * account before they ever see this button; signing in with Google outright would abandon all of
 * it silently. Only when Google says the identity already belongs to somebody else does this fall
 * back to signing in as that somebody — and it reports which of the two happened, so the screen can
 * say so rather than leaving a person to notice their progress is gone.
 *
 * [webClientId] is the OAuth client Firebase generates when Google sign-in is enabled; it reaches
 * this class as R.string.default_web_client_id, written into resources by the google-services
 * plugin. Without the provider enabled in the console there is no such client and nothing here can
 * work — which is a configuration failure, and it is reported as one rather than as a refusal.
 */
class FirebaseGoogleSignInRepository(
    private val auth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val webClientId: String,
    private val switchGuard: AccountSwitchGuard,
) : GoogleSignInRepository {
    override suspend fun linkGoogleAccount(host: AccountChooserHost): Result<GoogleLinkOutcome> {
        val activity = (host as? PlatformAccountChooserHost)?.platformHost as? Activity
        return when {
            activity == null ->
                Result.failure(IllegalArgumentException("Нужен экран, чтобы показать выбор аккаунта"))
            webClientId.isBlank() ->
                Result.failure(IllegalStateException("Вход через Google не настроен в проекте Firebase"))
            else -> runCatchingCancellable { attachIdentity(activity) }
        }
    }

    private suspend fun attachIdentity(activity: Activity): GoogleLinkOutcome {
        val request =
            GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                .build()
        val credential = credentialManager.getCredential(activity, request).credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("Google вернул неожиданный тип учётных данных")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

        val current = auth.currentUser ?: return signInAs(firebaseCredential)
        return try {
            current.linkWithCredential(firebaseCredential).await()
            GoogleLinkOutcome.LINKED
        } catch (collision: FirebaseAuthUserCollisionException) {
            // This Google account is already a player. Nothing can merge the two, so the choice is
            // to sign in as them or to refuse; signing in is what somebody tapping their own
            // account expects, and SWITCHED is how the screen learns to say it.
            logCollision(collision)
            signInAs(firebaseCredential)
        }
    }

    /**
     * Вход как другой игрок — с попыткой сначала доотправить очередь прежнего (AD-8).
     *
     * Слив идёт до `signInWithCredential`, а не после: после него прежнего `uid` уже не узнать, а
     * запись очереди принадлежит тому, кто её создал, и под новым аккаунтом не отправится никогда.
     * Не удалось слить — не повод запретить вход, но повод сказать словами.
     */
    private suspend fun signInAs(credential: AuthCredential): GoogleLinkOutcome {
        val readiness = switchGuard.flushBefore(auth.currentUser?.uid.orEmpty())
        auth.signInWithCredential(credential).await()
        return if (readiness.needsWarning) {
            GoogleLinkOutcome.SWITCHED_WITH_UNSENT
        } else {
            GoogleLinkOutcome.SWITCHED
        }
    }

    private fun logCollision(error: FirebaseAuthUserCollisionException) {
        android.util.Log.i(TAG, "Google account already in use, signing in instead", error)
    }

    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Throwable,
        ) {
            Result.failure(error)
        }

    private companion object {
        const val TAG = "GoogleSignIn"
    }
}
