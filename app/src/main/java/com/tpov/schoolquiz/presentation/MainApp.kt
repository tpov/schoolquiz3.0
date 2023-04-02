package com.tpov.schoolquiz.presentation

import android.app.Application
import com.tpov.schoolquiz.data.database.QuizDatabase
import com.tpov.schoolquiz.di.DaggerApplicationComponent
import kotlinx.coroutines.InternalCoroutinesApi

class MainApp : Application() {
    @InternalCoroutinesApi
    val database by lazy {
        QuizDatabase.getDatabase(this)
    }

    @InternalCoroutinesApi
    val component by lazy {
        DaggerApplicationComponent.factory().create(this)
    }
}
