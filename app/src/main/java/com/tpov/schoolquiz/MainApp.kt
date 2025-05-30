package com.tpov.schoolquiz

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.tpov.common.di.CommonComponent
import com.tpov.common.di.DaggerCommonComponent
import com.tpov.schoolquiz.di.ApplicationComponent
import com.tpov.schoolquiz.di.CommonComponentProvider
import com.tpov.schoolquiz.di.DaggerApplicationComponent

class MainApp : Application(), Configuration.Provider, CommonComponentProvider {

    lateinit var commonComponent: CommonComponent
    lateinit var applicationComponent: ApplicationComponent

    override fun onCreate() {
        super.onCreate()

        commonComponent = DaggerCommonComponent.factory().create(this)
        applicationComponent = DaggerApplicationComponent.factory().create(this, commonComponent)

        WorkManager.initialize(this, workManagerConfiguration)
        FirebaseApp.initializeApp(this)

        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
    }

    override fun provideCommonComponent(): CommonComponent {
        return commonComponent
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(applicationComponent.workerFactory())
            .build()
}
