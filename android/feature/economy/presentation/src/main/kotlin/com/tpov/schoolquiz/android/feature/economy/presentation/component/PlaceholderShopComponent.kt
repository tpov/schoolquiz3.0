package com.tpov.schoolquiz.android.feature.economy.presentation.component

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlaceholderShopComponent : ShopComponent {
    override val state: StateFlow<ShopViewState> =
        MutableStateFlow(
            ShopViewState(
                isLoading = false,
                message = ShopMessage.ShopUnavailable,
            ),
        )

    override fun obtainEvent(event: ShopViewEvent) = Unit
}
