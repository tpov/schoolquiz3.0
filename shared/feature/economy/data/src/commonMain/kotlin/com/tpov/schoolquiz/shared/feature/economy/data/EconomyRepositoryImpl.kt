package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.LessonUnlockRequest
import com.tpov.schoolquiz.shared.feature.economy.data.remote.ShopPurchaseRequest
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.LessonUnlockKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopPurchaseResult
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class EconomyRepositoryImpl(
    private val local: EconomyLocalDataSource,
    private val remote: EconomyRemoteDataSource,
    private val currentUidFlow: () -> Flow<String?>,
) : EconomyRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeBalance(): Flow<EconomyResourceBalance> =
        currentUidFlow().flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(EconomyResourceBalance.guest())
            } else {
                local.observe(uid).map { it ?: EconomyResourceBalance.guest() }
            }
        }

    override suspend fun currentBalance(): EconomyResourceBalance {
        val uid = currentUidFlow().first()
        if (uid.isNullOrBlank()) return EconomyResourceBalance.guest()
        return local.find(uid) ?: EconomyResourceBalance.guest()
    }

    override suspend fun purchase(itemId: ShopItemId): Result<ShopPurchaseResult> {
        if (itemId !in supportedRemotePurchases) {
            return Result.failure(IllegalStateException("Пока недоступно"))
        }
        val uid = currentUidFlow().first()
        if (uid.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Требуется авторизация"))
        }
        return runCatchingCancellable {
            val balance = remote.purchase(ShopPurchaseRequest(itemId.wireName))
            local.upsert(uid, balance)
            ShopPurchaseResult(
                itemId = itemId,
                balance = balance,
                message = itemId.successMessage(),
            )
        }
    }

    override suspend fun unlockLesson(
        lessonId: String,
        kind: LessonUnlockKind,
    ): Result<EconomyResourceBalance> {
        val uid = currentUidFlow().first()
        if (uid.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Требуется авторизация"))
        }
        return runCatchingCancellable {
            val balance = remote.unlockLesson(LessonUnlockRequest(lessonId, kind.wireName))
            // Write the returned balance straight back: it already carries the charge and the
            // new unlock, so the lesson list reacts without waiting for the next profile sync.
            local.upsert(uid, balance)
            balance
        }
    }

    override suspend fun referralProgram(): ReferralProgram {
        val uid = currentUidFlow().first().orEmpty().ifBlank { "guest" }
        return ReferralProgram(
            link = "schoolquiz://referral?id=$uid",
            invitedUsers = emptyList(),
        )
    }

    private fun ShopItemId.successMessage(): String =
        when (this) {
            ShopItemId.STANDARD_HEART_SLOT -> "Жизнь куплена"
            ShopItemId.GOLD_HEART -> "Золотая жизнь куплена"
            else -> "Покупка выполнена"
        }

    private companion object {
        val supportedRemotePurchases = setOf(ShopItemId.STANDARD_HEART_SLOT, ShopItemId.GOLD_HEART)
    }
}

private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
