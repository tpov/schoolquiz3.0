package com.tpov.schoolquiz.platform.android_services.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Связь по данным `ConnectivityManager`.
 *
 * «Онлайн» здесь означает не «есть подключение», а `NET_CAPABILITY_VALIDATED` — система сама
 * сходила наружу и получила ответ. Разница не косметическая: подключение к точке доступа,
 * которая требует входа через портал, выглядит как сеть, но ни один запрос через неё не пройдёт,
 * и без этой проверки игрок получал бы семидесятисекундный спиннер вместо честного «нет связи».
 */
class AndroidNetworkMonitor(
    context: Context,
) : NetworkMonitor {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun observeOnline(): Flow<Boolean> =
        callbackFlow {
            val manager = connectivity
            if (manager == null) {
                // Сервис недоступен — судить о связи нечем. Считаем, что она есть: запрет
                // обращаться к серверу навсегда хуже одной неудачной попытки.
                trySend(true)
                awaitClose { }
                return@callbackFlow
            }

            // Своё представление о связных сетях: колбэк приходит по одной сети за раз, а
            // ответить надо про все сразу — иначе отключение Wi-Fi при живой мобильной сети
            // прочитается как уход в офлайн.
            val online = mutableSetOf<Network>()

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        if (capabilities.isUsable()) online += network else online -= network
                        trySend(online.isNotEmpty())
                    }

                    override fun onLost(network: Network) {
                        online -= network
                        trySend(online.isNotEmpty())
                    }
                }

            trySend(manager.isOnlineNow())
            manager.registerNetworkCallback(usableNetworks(), callback)
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.conflate().distinctUntilChanged()

    override suspend fun isOnline(): Boolean = connectivity?.isOnlineNow() ?: true

    private fun ConnectivityManager.isOnlineNow(): Boolean = getNetworkCapabilities(activeNetwork)?.isUsable() ?: false

    private fun NetworkCapabilities.isUsable(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun usableNetworks(): NetworkRequest =
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
}
