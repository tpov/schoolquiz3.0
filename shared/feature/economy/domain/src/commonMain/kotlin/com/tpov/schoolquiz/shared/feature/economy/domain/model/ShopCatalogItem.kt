package com.tpov.schoolquiz.shared.feature.economy.domain.model

data class ShopCatalogItem(
    val id: ShopItemId,
    val title: String,
    val description: String,
    val category: ShopItemCategory,
    val price: ShopPrice?,
    val isAvailable: Boolean,
    val unavailableReason: String? = null,
)
