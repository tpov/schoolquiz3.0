package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId

sealed interface ShopViewEvent {
    data class SelectTab(val tab: ShopTab) : ShopViewEvent

    data class Purchase(val itemId: ShopItemId) : ShopViewEvent

    data object MessageShown : ShopViewEvent
}
