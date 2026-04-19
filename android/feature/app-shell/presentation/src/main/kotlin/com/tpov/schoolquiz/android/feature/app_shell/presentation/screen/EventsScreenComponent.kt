package com.tpov.schoolquiz.android.feature.app_shell.presentation.screen

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig

sealed interface EventsScreenComponent {
    data class Placeholder(val config: EventsConfig) : EventsScreenComponent
}
