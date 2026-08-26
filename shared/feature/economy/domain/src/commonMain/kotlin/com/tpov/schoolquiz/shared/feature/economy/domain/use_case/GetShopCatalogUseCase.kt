package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCatalogItem
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCurrency
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemCategory
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopPrice

/**
 * What the shelf holds, as pure data.
 *
 * Titles and availability wording are resolved in the UI from [ShopItemId]; the domain carries
 * neither, so no language leaks past this boundary.
 */
class GetShopCatalogUseCase {
    fun execute(balance: EconomyResourceBalance): List<ShopCatalogItem> =
        listOf(
            standardHeartSlot(balance),
            goldHeart(balance),
            quizSlot(),
            donateGooglePlay(),
            referralProgram(),
            nicknameMarket(),
            adRewardBox(),
        )

    private fun standardHeartSlot(balance: EconomyResourceBalance): ShopCatalogItem {
        val maxed = balance.standardHearts >= EconomyResourceBalance.MaxStandardHearts
        return ShopCatalogItem(
            id = ShopItemId.STANDARD_HEART_SLOT,
            title = "",
            description = "",
            category = ShopItemCategory.RESOURCES,
            price = ShopPrice(standardHeartCost(balance.standardHearts), ShopCurrency.NOLICS),
            isAvailable = !maxed,
        )
    }

    private fun goldHeart(balance: EconomyResourceBalance): ShopCatalogItem {
        val maxed = balance.goldHearts >= EconomyResourceBalance.MaxGoldHearts
        return ShopCatalogItem(
            id = ShopItemId.GOLD_HEART,
            title = "",
            description = "",
            category = ShopItemCategory.RESOURCES,
            price = ShopPrice(GOLD_HEART_COST, ShopCurrency.GOLD),
            isAvailable = !maxed,
        )
    }

    private fun quizSlot(): ShopCatalogItem =
        ShopCatalogItem(
            id = ShopItemId.QUIZ_SLOT,
            title = "",
            description = "",
            category = ShopItemCategory.QUESTS,
            price = ShopPrice(QUIZ_SLOT_COST, ShopCurrency.NOLICS),
            isAvailable = false,
        )

    private fun adRewardBox(): ShopCatalogItem =
        ShopCatalogItem(
            id = ShopItemId.AD_REWARD_BOX,
            title = "",
            description = "",
            category = ShopItemCategory.BONUSES,
            price = ShopPrice(AD_REWARD_BOX_COST, ShopCurrency.ADS),
            isAvailable = false,
        )

    private fun donateGooglePlay(): ShopCatalogItem =
        ShopCatalogItem(
            id = ShopItemId.DONATE_GOOGLE_PLAY,
            title = "",
            description = "",
            category = ShopItemCategory.SUPPORT,
            price = ShopPrice(0L, ShopCurrency.EXTERNAL),
            isAvailable = false,
        )

    private fun referralProgram(): ShopCatalogItem =
        ShopCatalogItem(
            id = ShopItemId.REFERRAL_PROGRAM,
            title = "",
            description = "",
            category = ShopItemCategory.COMMUNITY,
            price = ShopPrice(0L, ShopCurrency.FREE),
            isAvailable = true,
        )

    private fun nicknameMarket(): ShopCatalogItem =
        ShopCatalogItem(
            id = ShopItemId.NICKNAME_MARKET,
            title = "",
            description = "",
            category = ShopItemCategory.COMMUNITY,
            // Free to open. Names inside cost gold, but the entry itself is a door, not a purchase.
            price = ShopPrice(0L, ShopCurrency.FREE),
            isAvailable = true,
        )

    companion object {
        private val STANDARD_HEART_COSTS = listOf(1_000L, 2_000L, 5_000L, 10_000L, 20_000L)
        private const val GOLD_HEART_COST = 10L
        private const val QUIZ_SLOT_COST = 1_000L
        private const val AD_REWARD_BOX_COST = 5L

        fun standardHeartCost(currentHearts: Int): Long {
            val index = currentHearts.coerceIn(0, STANDARD_HEART_COSTS.lastIndex)
            return STANDARD_HEART_COSTS[index]
        }
    }
}
