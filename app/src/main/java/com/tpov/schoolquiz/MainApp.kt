package com.tpov.schoolquiz

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.tpov.common.data.core.Core
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

        FirebaseApp.initializeApp(this)
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        val currentUser: FirebaseUser? = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            FirebaseAuth.getInstance().signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Успешная аутентификация
                        Log.d("FirebaseAuth", "Анонимный пользователь создан")
                    } else {
                        // Обработка ошибки аутентификации
                        Log.e("FirebaseAuth", "Ошибка анонимной аутентификации", task.exception)
                    }
                }
        } else {
            Log.d("FirebaseAuth", "Пользователь уже авторизован: ${currentUser.uid}")
        }
//        FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.addOnSuccessListener {
//            Core.token = it.token ?: ""
//            println("Firebase Auth Token: ${it.token}")
//        }!!.addOnFailureListener {
//            println("Error fetching Firebase Auth Token: ${it.message}")
//        }
        Firebase.appCheck.getAppCheckToken(false).addOnSuccessListener {
           Core.token = it.token
        }
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
