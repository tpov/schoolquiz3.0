package com.tpov.schoolquiz.shared.feature.app_shell.domain.state

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent

/**
 * Result of a domain state transition.
 *
 * Wraps the new state plus any domain-to-UI events emitted during the transition.
 * Pure domain never throws — all outcomes are encoded here.
 */
data class TransitionResult(
    val newState: AppShellState,
    val events: List<RootEvent> = emptyList(),
)
