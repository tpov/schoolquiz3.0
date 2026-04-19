package com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case

import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.onActiveTabRetap
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState

/**
 * Handles re-tap on the currently active bottom tab.
 *
 * Thin wrapper over [onActiveTabRetap] pure function.
 * Implements Re-tap FSM (Business Rule #10, State Matrix re-tap rows 1-2):
 *  - backStack not empty → POP_TO_ROOT + clear backStack
 *  - backStack empty     → NO_OP
 *
 * Returns the new [AppShellState] (possibly unchanged) and the [RetapOutcome].
 * The caller (ViewModel) uses [RetapOutcome.NO_OP] to trigger scroll-to-top via UI hook.
 *
 * Synchronous — retap policy is pure computation.
 */
class OnTabRetapUseCase {
    operator fun invoke(currentState: AppShellState, tab: Tab): Pair<AppShellState, RetapOutcome> =
        onActiveTabRetap(currentState, tab)
}
