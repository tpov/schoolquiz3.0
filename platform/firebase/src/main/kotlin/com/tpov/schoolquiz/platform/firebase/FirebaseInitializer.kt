package com.tpov.schoolquiz.platform.firebase

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage

fun initializeFirebaseSecurity(
    app: Application,
    useDebugAppCheckProvider: Boolean = false,
    appCheckDebugSecret: String? = null,
    useEmulators: Boolean = false,
) {
    val firebaseApp = FirebaseApp.initializeApp(app) ?: FirebaseApp.getInstance()
    if (useEmulators) {
        // Fail closed before anonymous sign-in or Koin resolves any remote data source.
        require(firebaseApp.options.projectId == "demo-schoolquiz-mvp") {
            "The isolated MVP build requires the demo Firebase configuration"
        }
        val host = "127.0.0.1"
        FirebaseAuth.getInstance(firebaseApp).useEmulator(host, 9099)
        FirebaseFirestore.getInstance(firebaseApp).useEmulator(host, 8080)
        FirebaseFunctions.getInstance(firebaseApp).useEmulator(host, 5001)
        FirebaseDatabase.getInstance(firebaseApp).useEmulator(host, 9000)
        FirebaseStorage.getInstance(firebaseApp).useEmulator(host, 9199)
        // App Check/Integrity has no local emulator. The demo never contacts its live service.
        return
    }
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
