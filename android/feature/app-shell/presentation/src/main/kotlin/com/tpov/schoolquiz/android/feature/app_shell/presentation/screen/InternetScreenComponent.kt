package com.tpov.schoolquiz.android.feature.app_shell.presentation.screen

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig

sealed interface InternetScreenComponent {
    data class Placeholder(val config: InternetConfig) : InternetScreenComponent
}
