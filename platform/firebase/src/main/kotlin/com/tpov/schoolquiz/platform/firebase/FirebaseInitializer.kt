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
    emulatorHost: String = "127.0.0.1",
) {
    val firebaseApp = FirebaseApp.initializeApp(app) ?: FirebaseApp.getInstance()
    if (useEmulators) {
        // Fail closed before anonymous sign-in or Koin resolves any remote data source.
        require(firebaseApp.options.projectId == "demo-schoolquiz-mvp") {
            "The isolated MVP build requires the demo Firebase configuration"
        }
        // Device-proof escape hatch (story 5.13): adb reverse data forwarding proved broken on
        // the physical device (platform-tools 36 ↔ Android 17 adbd — TCP accepts, no bytes flow),
        // so a rig run points the same ports at a LAN forwarder. Default stays loopback.
        val host = emulatorHost.ifBlank { "127.0.0.1" }
        FirebaseAuth.getInstance(firebaseApp).useEmulator(host, MvpEmulatorPorts.AUTH)
        FirebaseFirestore.getInstance(firebaseApp).useEmulator(host, MvpEmulatorPorts.FIRESTORE)
        FirebaseFunctions.getInstance(firebaseApp).useEmulator(host, MvpEmulatorPorts.FUNCTIONS)
        FirebaseDatabase.getInstance(firebaseApp).useEmulator(host, MvpEmulatorPorts.DATABASE)
        FirebaseStorage.getInstance(firebaseApp).useEmulator(host, MvpEmulatorPorts.STORAGE)
        // App Check/Integrity has no local emulator, and the demo never contacts its live
        // service. Но вызываемая функция склеивает App Check-задачу с auth-токеном в один whenAll
        // (FirebaseContextProvider.getContext), поэтому без установленной фабрики любой callable
        // падает до сети — «1 out of 2 underlying tasks failed». Эмулятор функции токен не
        // проверяет («app:MISSING» проходит), поэтому demo-ветка ставит локальную фабрику: она
        // отвечает сразу и живого App Check-бэкенда не касается — debug-провайдер обменивает
        // токен по сети и после переустановок упирается в его «Too many attempts».
        FirebaseAppCheck
            .getInstance(firebaseApp)
            .installAppCheckProviderFactory(DemoLocalAppCheckProviderFactory)
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

private object MvpEmulatorPorts {
    const val AUTH = 9099
    const val FIRESTORE = 8080
    const val FUNCTIONS = 5001
    const val DATABASE = 9000
    const val STORAGE = 9199
}

internal fun debugAppCheckProviderFactoryOrNull(): AppCheckProviderFactory? =
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
