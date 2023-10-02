package com.tpov.schoolquiz.presentation

import android.app.Application
import com.google.firebase.FirebaseApp
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

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        //val firebaseAnalytics = FirebaseAnalytics.getInstance(this)
       //val bundle = Bundle()
       //bundle.putString("item_name", "example")
       //firebaseAnalytics.logEvent("view_item", bundle)
    }


}
