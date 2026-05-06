package com.tpov.schoolquiz.android.feature.economy.presentation.component

import kotlinx.coroutines.flow.StateFlow

interface ShopComponent {
    val state: StateFlow<ShopViewState>

    fun obtainEvent(event: ShopViewEvent)
}
