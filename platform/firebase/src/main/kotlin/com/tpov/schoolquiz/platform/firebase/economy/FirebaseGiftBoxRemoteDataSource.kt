package com.tpov.schoolquiz.platform.firebase.economy

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxReward
import kotlinx.coroutines.tasks.await

class FirebaseGiftBoxRemoteDataSource(
    private val functions: FirebaseFunctions,
) : GiftBoxRemoteDataSource {
    override suspend fun openGiftBox(): GiftBoxReward {
        val data =
            functions
                .getHttpsCallable(OPEN_GIFT_BOX)
                .call()
                .await()
                .data
        return (data as? Map<*, *>).toGiftBoxReward()
    }

    private fun Map<*, *>?.toGiftBoxReward(): GiftBoxReward {
        val type = string(TYPE)
        val amount = long(AMOUNT).coerceAtLeast(0L)
        return when (type) {
            ADD_NOLICS -> GiftBoxReward.Nolics(amount)
            ADD_GOLD -> GiftBoxReward.Gold(amount)
            DATE_PREMIUM -> GiftBoxReward.Premium(amount)
            LOGO -> GiftBoxReward.Logo(itemName = string(ITEM_NAME).ifBlank { "Logo" }, amount = amount)
            TROPHY -> GiftBoxReward.Trophy(amount)
            else -> error("Unknown gift box reward type: $type")
        }
    }

    private fun Map<*, *>?.string(field: String): String = this?.get(field)?.toString().orEmpty()

    private fun Map<*, *>?.long(field: String): Long =
        when (val value = this?.get(field)) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> 0L
        }

    private companion object {
        const val OPEN_GIFT_BOX = "openGiftBox"
        const val TYPE = "type"
        const val AMOUNT = "amount"
        const val ITEM_NAME = "itemName"
        const val ADD_NOLICS = "addNolics"
        const val ADD_GOLD = "addGold"
        const val DATE_PREMIUM = "datePremium"
        const val LOGO = "logo"
        const val TROPHY = "trophy"
    }
}
