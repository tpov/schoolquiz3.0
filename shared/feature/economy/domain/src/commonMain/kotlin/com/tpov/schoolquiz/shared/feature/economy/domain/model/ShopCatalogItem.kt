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
    /**
     * Запись магазина об этом товаре, если он продаётся за реальные деньги.
     *
     * Цена — [StoreProduct.formattedPrice]: строка, отформатированная самим магазином в валюте и
     * локали игрока. Экран показывает её как есть и ничего не форматирует и не пересчитывает
     * (NFR4); сколько золота за SKU выдаёт сервер, на устройстве неизвестно (NFR3). `null` — товар
     * не продаётся в магазине или магазин ещё не ответил.
     */
    val storeProduct: StoreProduct? = null,
)
