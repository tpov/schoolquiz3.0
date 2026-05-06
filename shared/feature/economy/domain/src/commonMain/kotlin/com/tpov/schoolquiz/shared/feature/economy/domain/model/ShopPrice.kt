package com.tpov.schoolquiz.shared.feature.economy.domain.model

data class ShopPrice(
    val amount: Long,
    val currency: ShopCurrency,
) {
    val label: String
        get() =
            when (currency) {
                ShopCurrency.NOLICS -> "$amount ноликов"
                ShopCurrency.GOLD -> "$amount золота"
                ShopCurrency.ADS -> "$amount объявлений"
                ShopCurrency.EXTERNAL -> "Google Play"
                ShopCurrency.FREE -> "Бесплатно"
            }
}
