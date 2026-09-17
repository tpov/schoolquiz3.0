package com.tpov.schoolquiz.platform.firebase.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI push-фичи (Story 10.2, NFR5): реализации и их привязки — в одном пакете `push`.
 *
 * @param nowMs общий источник времени приложения (AD-9). Приходит из composition root — тот же,
 *   что у очереди и состояния синхронизации; своих часов у адаптеров нет.
 */
fun pushModule(nowMs: () -> Long): Module =
    module {
        single<FcmTokenSource> { FcmTokenSource(fetchToken = { FirebaseMessaging.getInstance().token.await() }) }
            .bind(DeviceTokenSource::class)
        single<DeviceTokenStore> { FirestoreDeviceTokenStore(firestore = get()) }
        single<DeviceTokenRegistrar> {
            DeviceTokenRegistrar(
                authRepository = get(),
                tokenSource = get<DeviceTokenSource>(),
                store = get(),
                nowMs = nowMs,
                scope = get<CoroutineScope>(),
            )
        }.bind(DeviceTokenRelease::class)
        single<PushNotifier> { AndroidPushNotifier(androidContext()) }
        single<ForeignTokenDetacher> {
            CallableForeignTokenDetacher(
                functions = get(),
                tokenSource = get<DeviceTokenSource>(),
                scope = get<CoroutineScope>(),
            )
        }
        single<PushMessageHandler> {
            PushMessageHandler(
                scheduler = get(),
                notifier = get(),
                authRepository = get(),
                foreignTokenDetacher = get(),
            )
        }
    }
