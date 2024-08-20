package com.tpov.schoolquiz

import android.app.Application
import com.tpov.common.data.database.CommonDatabase
import com.tpov.common.di.CommonComponent
import com.tpov.common.di.DaggerCommonComponent
import kotlinx.coroutines.InternalCoroutinesApi

class MainApp : Application() {

    @OptIn(InternalCoroutinesApi::class)
    val appDatabase by lazy {
        CommonDatabase.getDatabase(this)
    }

    lateinit var commonComponent: CommonComponent

    override fun onCreate() {
        super.onCreate()
        // FirebaseApp.initializeApp(this)

        commonComponent = DaggerCommonComponent.factory().create(this)
    }
}