package com.tpov.schoolquiz.platform.firebase

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

fun initializeFirebaseSecurity(
    app: Application,
    useDebugAppCheckProvider: Boolean = false,
    appCheckDebugSecret: String? = null,
) {
    val firebaseApp = FirebaseApp.initializeApp(app) ?: FirebaseApp.getInstance()
    val providerFactory =
        if (useDebugAppCheckProvider) {
            appCheckDebugSecret
                ?.takeIf { it.isNotBlank() }
                ?.let { seedDebugAppCheckSecret(app, firebaseApp.persistenceKey, it) }
            debugAppCheckProviderFactoryOrNull()
                ?: PlayIntegrityAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }

    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(providerFactory)
}

private fun debugAppCheckProviderFactoryOrNull(): AppCheckProviderFactory? =
    runCatching {
        Class
            .forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
            .getMethod("getInstance")
            .invoke(null) as? AppCheckProviderFactory
    }.getOrNull()

private fun seedDebugAppCheckSecret(
    app: Application,
    firebasePersistenceKey: String,
    debugSecret: String,
) {
    app
        .getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.$firebasePersistenceKey",
            Application.MODE_PRIVATE,
        )
        .edit()
        .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", debugSecret)
        .apply()
}
