package com.tpov.schoolquiz.apps.android_next

import android.app.Application
import com.tpov.schoolquiz.android.feature.app_shell.presentation.di.appShellPresentationModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseModule
import com.tpov.schoolquiz.platform.firebase.initializeFirebaseSecurity
import com.tpov.schoolquiz.shared.feature.app_shell.data.di.appShellDataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeFirebaseSecurity(this)
        startKoin {
            androidContext(this@AppApplication)
            modules(
                firebaseModule,
                appShellDataModule,
                appShellPresentationModule,
            )
        }
    }
}
