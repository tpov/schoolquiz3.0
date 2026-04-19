package com.tpov.schoolquiz.android.feature.app_shell.presentation.screen

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig

sealed interface LocalScreenComponent {
    data class Placeholder(val config: LocalConfig) : LocalScreenComponent
}
