package com.tpov.schoolquiz.apps.android_next.di

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.tpov.schoolquiz.platform.firebase.auth.FirebaseGoogleSignInRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.GoogleSignInRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Signing in with Google, wired where the generated resources live.
 *
 * This belongs to the app module rather than to platform/firebase because the OAuth client id is
 * not written by hand anywhere: the google-services plugin generates it into this module's
 * resources from google-services.json, and it only exists once the provider is switched on in the
 * Firebase console.
 */
val authModule =
    module {
        single { CredentialManager.create(androidContext()) }
        single<GoogleSignInRepository> {
            FirebaseGoogleSignInRepository(
                auth = FirebaseAuth.getInstance(),
                credentialManager = get(),
                webClientId = androidContext().googleWebClientId(),
                switchGuard = get(),
            )
        }
    }

/**
 * The OAuth client id, looked up by name instead of through R.
 *
 * The resource is generated, and it is absent whenever Google sign-in has not been enabled for the
 * project. Referring to it through R would turn that configuration gap into a build failure for
 * everyone; looked up by name it becomes an empty string, which the repository reports as "not
 * configured" to the one person who tapped the button.
 */
private fun Context.googleWebClientId(): String {
    val id = resources.getIdentifier("default_web_client_id", "string", packageName)
    return if (id == 0) "" else getString(id)
}
