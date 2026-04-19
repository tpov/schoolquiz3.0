package com.tpov.schoolquiz.android.feature.app_shell.presentation.screen

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.ShopConfig

sealed interface ShopScreenComponent {
    data class Placeholder(val config: ShopConfig) : ShopScreenComponent
}
