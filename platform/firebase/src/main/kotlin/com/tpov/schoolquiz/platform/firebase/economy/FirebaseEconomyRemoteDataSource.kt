package com.tpov.schoolquiz.platform.firebase.economy

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.tpov.schoolquiz.platform.firebase.network.toSyncError
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.LessonUnlockRequest
import com.tpov.schoolquiz.shared.feature.economy.data.remote.ShopPurchaseRequest
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import kotlinx.coroutines.tasks.await
import java.io.IOException

class FirebaseEconomyRemoteDataSource(
    private val functions: FirebaseFunctions,
    private val networkMonitor: NetworkMonitor,
) : EconomyRemoteDataSource {
    override suspend fun purchase(request: ShopPurchaseRequest): EconomyResourceBalance =
        call(APPLY_SHOP_PURCHASE, mapOf(ITEM_ID to request.itemId))

    override suspend fun unlockLesson(request: LessonUnlockRequest): EconomyResourceBalance =
        call(UNLOCK_LESSON, mapOf(LESSON_ID to request.lessonId, KIND to request.kind))

    /**
     * Один путь для обоих вызовов: спросить про связь, сходить с таймаутом, прочитать неудачу
     * как ветвь [SyncError].
     *
     * Проверка связи до вызова — не оптимизация, а весь смысл: без неё покупка в офлайне держит
     * спиннер до истечения таймаута, вместо того чтобы сразу сказать, что нужен интернет.
     */
    private suspend fun call(
        name: String,
        payload: Map<String, Any>,
    ): EconomyResourceBalance {
        if (!networkMonitor.isOnline()) failWith(SyncError.NoNetwork)
        val data =
            try {
                functions
                    .getHttpsCallable(name)
                    .withAppTimeout()
                    .call(payload)
                    .await()
                    .data as? Map<*, *>
            } catch (e: FirebaseFunctionsException) {
                failWith(e.toSyncError(), e)
            } catch (e: IOException) {
                // До сервера не доехали: ответа не было, повторять безопасно.
                failWith(SyncError.NoNetwork, e)
            }
        return data.map(BALANCE).toBalance()
    }

    private fun failWith(
        error: SyncError,
        cause: Throwable? = null,
    ): Nothing = throw SyncFailure(error, cause)

    private fun Map<*, *>?.toBalance(): EconomyResourceBalance =
        EconomyResourceBalance(
            hasPremium = boolean(HAS_PREMIUM),
            streakDays = long(STREAK_DAYS).toInt().coerceAtLeast(0),
            stars = long(STARS).coerceAtLeast(0L),
            nolics = long(NOLICS).coerceAtLeast(0L),
            standardHearts = long(STANDARD_HEARTS).toInt().coerceIn(0, EconomyResourceBalance.MaxStandardHearts),
            goldHearts = long(GOLD_HEARTS).toInt().coerceIn(0, EconomyResourceBalance.MaxGoldHearts),
            gold = long(GOLD).coerceAtLeast(0L),
            lessonUnlocks = stringSet(LESSON_UNLOCKS),
        )

    private fun Map<*, *>?.map(field: String): Map<*, *> = this?.get(field) as? Map<*, *> ?: emptyMap<Any, Any>()

    private fun Map<*, *>?.boolean(field: String): Boolean = this?.get(field) as? Boolean ?: false

    private fun Map<*, *>?.stringSet(field: String): Set<String> =
        (this?.get(field) as? List<*>)?.mapNotNull { it as? String }?.toSet().orEmpty()

    private fun Map<*, *>?.long(field: String): Long =
        when (val value = this?.get(field)) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> 0L
        }

    private companion object {
        const val APPLY_SHOP_PURCHASE = "applyShopPurchase"
        const val ITEM_ID = "itemId"
        const val BALANCE = "balance"
        const val HAS_PREMIUM = "hasPremium"
        const val STREAK_DAYS = "streakDays"
        const val STARS = "stars"
        const val NOLICS = "nolics"
        const val UNLOCK_LESSON = "unlockLesson"
        const val LESSON_ID = "lessonId"
        const val KIND = "kind"
        const val STANDARD_HEARTS = "standardHearts"
        const val GOLD_HEARTS = "goldHearts"
        const val GOLD = "gold"
        const val LESSON_UNLOCKS = "lessonUnlocks"
    }
}
