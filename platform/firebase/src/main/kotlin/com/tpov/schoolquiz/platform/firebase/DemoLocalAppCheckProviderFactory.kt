package com.tpov.schoolquiz.platform.firebase

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.AppCheckToken

/**
 * App Check demo-сборки: постоянный локальный токен вместо похода в живой бэкенд.
 *
 * Эмулятор функции токен не проверяет, но SDK склеивает его получение с auth-токеном в один
 * `whenAll` до сети — упавший провайдер валит каждый callable до эмулятора. Debug-провайдер
 * для токена обменивается с живым App Check-бэкендом и после переустановок упирается в его
 * rate limit («Too many attempts»); локальный отвечает сразу, поэтому demo-путь детерминирован
 * и работает без внешней сети. Токен ничего не открывает: бэкенды demo его не валидируют.
 */
internal object DemoLocalAppCheckProviderFactory : AppCheckProviderFactory {
    override fun create(firebaseApp: FirebaseApp): AppCheckProvider = DemoLocalAppCheckProvider
}

private object DemoLocalAppCheckProvider : AppCheckProvider {
    override fun getToken(): Task<AppCheckToken> =
        Tasks.forResult(
            object : AppCheckToken() {
                override fun getToken(): String = TOKEN

                override fun getExpireTimeMillis(): Long = System.currentTimeMillis() + TTL_MS
            },
        )

    private const val TOKEN = "demo-local-appcheck"
    private const val TTL_MS = 60L * 60 * 1000
}
