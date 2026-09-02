package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeRules
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.canBuySlot
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
/**
 * Витрина магазина.
 *
 * Цены слотов и потолки берутся из таблицы настроек, которой владеет сервер: подкрутить лестницу
 * или потолок должно быть решением на сервере, а не сборкой. До первой синхронизации таблица —
 * загрузочная копия; она же и подставляется по умолчанию, чтобы тесты и вызывающие без таблицы
 * остались простыми.
 */
class GetShopCatalogUseCase(
    private val constants: () -> EconomyConstants = { EconomyConstants.BOOTSTRAP },
) {
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
        val rules = constants().standard
        return ShopCatalogItem(
            id = ShopItemId.STANDARD_HEART_SLOT,
            title = "",
            description = "",
            category = ShopItemCategory.RESOURCES,
            price = ShopPrice(rules.slotPrice(balance.standardHearts), rules.currency),
            // Понижённый потолок не отбирает купленное, но и купить сверх него не даёт.
            isAvailable = rules.canBuySlot(balance.standardHearts),
            nextPrice = rules.nextSlotPrice(balance.standardHearts),
        )
    }

    private fun goldHeart(balance: EconomyResourceBalance): ShopCatalogItem {
        // Лестница `1, 2, 3` золотом вместо плоских десяти: все три стоят шесть, один раз.
        val rules = constants().plasma
        return ShopCatalogItem(
            id = ShopItemId.GOLD_HEART,
            title = "",
            description = "",
            category = ShopItemCategory.RESOURCES,
            price = ShopPrice(rules.slotPrice(balance.goldHearts), rules.currency),
            isAvailable = rules.canBuySlot(balance.goldHearts),
            nextPrice = rules.nextSlotPrice(balance.goldHearts),
        )
    }

    /** Цена покупки после этой — или `null`, если после этой будет потолок. */
    private fun ChargeRules.nextSlotPrice(owned: Int): ShopPrice? =
        if (canBuySlot(owned) && canBuySlot(owned + 1)) ShopPrice(slotPrice(owned + 1), currency) else null

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
        private const val QUIZ_SLOT_COST = 1_000L
        private const val AD_REWARD_BOX_COST = 5L
    }
}
