package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

/**
 * Domain-to-UI event type.
 *
 * The only channel by which the domain signals the UI to perform a platform action.
 * SystemBack: UI should call activity.finish() / moveTaskToBack().
 *
 * In phase-01 Decompose integration, these events will be collected from a Flow/Channel.
 * In pure domain, they are returned as List<RootEvent> inside TransitionResult.
 */
sealed interface RootEvent {
    /**
     * Domain FSM reached the "exit" state:
     * backStack empty + activeTab == LOCAL + drawer closed.
     */
    data object SystemBack : RootEvent

    /** Developer mode was successfully activated via version tap sequence. */
    data object DevModeActivated : RootEvent

    /** Version tap sequence received but developer mode was already active. */
    data object DevModeAlreadyActive : RootEvent

    /** A profile sync operation has been initiated via onSyncNow(). */
    data object SyncStarted : RootEvent
}
