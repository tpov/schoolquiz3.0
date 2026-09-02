package com.tpov.schoolquiz.shared.feature.economy.domain.model

data class ShopCatalogItem(
    val id: ShopItemId,
    val title: String,
    val description: String,
    val category: ShopItemCategory,
    val price: ShopPrice?,
    val isAvailable: Boolean,
    val unavailableReason: String? = null,
    /**
     * Чего будет стоить следующая покупка того же, если она возможна.
     *
     * Для слотов, которые дорожают по лестнице: строка под названием отвечает на следующий
     * вопрос игрока, а лестница живёт в серверной таблице, и экран не должен держать её копию.
     * `null` — следующей покупки не будет: потолок.
     */
    val nextPrice: ShopPrice? = null,
)
