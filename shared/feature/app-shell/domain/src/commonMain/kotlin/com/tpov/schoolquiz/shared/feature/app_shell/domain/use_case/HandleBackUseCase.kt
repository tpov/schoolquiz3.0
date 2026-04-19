package com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case

import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.onBack
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.TransitionResult

/**
 * Handles a back navigation event (system back button or gesture).
 *
 * Thin wrapper over [onBack] pure function.
 * Implements Back-policy FSM (Business Rule #9, State Matrix back-policy rows 1-6):
 *  1. drawer open → close drawer
 *  2. backStack not empty → pop
 *  3. backStack empty + tab != LOCAL → switch to LOCAL
 *  4. backStack empty + tab == LOCAL → emit RootEvent.SystemBack
 *
 * Synchronous — back policy is pure computation.
 */
class HandleBackUseCase {
    operator fun invoke(currentState: AppShellState): TransitionResult =
        onBack(currentState)
}
