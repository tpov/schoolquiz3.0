package com.tpov.schoolquiz.android.feature.economy.presentation.component

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlaceholderShopComponent : ShopComponent {
    override val state: StateFlow<ShopViewState> =
        MutableStateFlow(
            ShopViewState(
                isLoading = false,
                message = "Магазин пока недоступен",
            ),
        )

    override fun obtainEvent(event: ShopViewEvent) = Unit
}
