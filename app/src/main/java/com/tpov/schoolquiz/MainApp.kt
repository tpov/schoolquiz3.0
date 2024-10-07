package com.tpov.schoolquiz

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.tpov.common.data.core.Core
import com.tpov.common.di.CommonComponent
import com.tpov.common.di.DaggerCommonComponent
import com.tpov.schoolquiz.di.ApplicationComponent
import com.tpov.schoolquiz.di.DaggerApplicationComponent
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

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

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            FirebaseAuth.getInstance().signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result?.user?.uid
                        if (token != null) {
                            assignTpovIdViaHttp(token)
                        }
                    }
                }
        } else {
            val token = currentUser.uid
            assignTpovIdViaHttp(token)
        }
    }

    fun assignTpovIdViaHttp(token: String) {
        Core.token = token

        val url = "https://get-tpovid-762375057396.us-central1.run.app/assignTpovId"
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val json = JSONObject().apply {
            put("token", token)
        }
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = json.toString().toRequestBody(mediaType)

        // Получаем ID токен пользователя
        FirebaseAuth.getInstance().currentUser?.getIdToken(false)
            ?.addOnSuccessListener { idTokenResult ->
                val idToken = idTokenResult.token

                // Получаем токен App Check
                FirebaseAppCheck.getInstance().getAppCheckToken(false)
                    .addOnSuccessListener { appCheckTokenResult ->
                        val appCheckToken = appCheckTokenResult.token

                        // Создаём запрос с добавлением токена App Check
                        val request = Request.Builder()
                            .url(url)
                            .addHeader("Authorization", "Bearer $idToken")
                            .addHeader("X-Firebase-AppCheck", appCheckToken)
                            .post(body)
                            .build()

                        client.newCall(request).enqueue(object : okhttp3.Callback {
                            override fun onFailure(call: okhttp3.Call, e: IOException) {
                                Log.e("FirebaseAuth", "Ошибка при выполнении запроса", e)
                            }

                            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                                response.use {
                                    if (it.isSuccessful) {
                                        val responseBody = it.body?.string()
                                        val responseJson = JSONObject(responseBody ?: "{}")
                                        val tpovId = responseJson.optInt("tpovId")
                                        Log.d("FirebaseAuth", "Получен tpovId: $tpovId")
                                        Core.tpovId = tpovId
                                    } else {
                                        val errorBody = it.body?.string()
                                        Log.e("FirebaseAuth", "Ошибка в ответе: ${it.code}, $errorBody")
                                    }
                                }
                            }
                        })
                    }
                    .addOnFailureListener { exception ->
                        Log.e("FirebaseAuth", "Не удалось получить токен App Check", exception)
                    }
            }
            ?.addOnFailureListener { exception ->
                Log.e("FirebaseAuth", "Ошибка при получении ID токена", exception)
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
