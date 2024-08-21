package com.tpov.schoolquiz

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tpov.common.data.database.CommonDatabase
import com.tpov.common.di.CommonComponent
import com.tpov.common.di.DaggerCommonComponent
import com.tpov.schoolquiz.di.ApplicationComponent
import com.tpov.schoolquiz.di.DaggerApplicationComponent
import kotlinx.coroutines.InternalCoroutinesApi

class MainApp : Application() {

    @OptIn(InternalCoroutinesApi::class)
    val appDatabase by lazy {
        CommonDatabase.getDatabase(this)
    }

    lateinit var commonComponent: CommonComponent
    lateinit var applicationComponent: ApplicationComponent

    override fun onCreate() {
        super.onCreate()

        commonComponent = DaggerCommonComponent.factory().create(this)
        applicationComponent = DaggerApplicationComponent.factory().create(this)

        // Инициализация WorkManager с DaggerWorkerFactory
        WorkManager.initialize(
            this,
            Configuration.Builder()
                .setWorkerFactory(applicationComponent.workerFactory())
                .build()
        )
    }
}
