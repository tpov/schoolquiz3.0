package com.tpov.schoolquiz

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tpov.common.di.CommonComponent
import com.tpov.common.di.DaggerCommonComponent
import com.tpov.schoolquiz.di.ApplicationComponent
import com.tpov.schoolquiz.di.DaggerApplicationComponent


class MainApp : Application(), Configuration.Provider {

    lateinit var commonComponent: CommonComponent
    lateinit var applicationComponent: ApplicationComponent

    override fun onCreate() {
        super.onCreate()

        commonComponent = DaggerCommonComponent.factory().create(this)
        applicationComponent = DaggerApplicationComponent.factory().create(this)

        WorkManager.initialize(
            this,
            workManagerConfiguration
        )
    }
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(applicationComponent.workerFactory())
            .build()
}
