package com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case

import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.navigate
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.TransitionResult

/**
 * Applies a navigation [Destination] to the current [AppShellState].
 *
 * Thin wrapper over the pure [navigate] function from [logic].
 * The use case layer is responsible for orchestration; business rules live in pure core.
 *
 * This is synchronous because navigation state transitions are pure (no I/O).
 * The caller (ViewModel) stores the returned [TransitionResult.newState] and
 * dispatches [TransitionResult.events] to the UI.
 */
class NavigateUseCase {
    operator fun invoke(currentState: AppShellState, destination: Destination): TransitionResult =
        navigate(currentState, destination)
}
