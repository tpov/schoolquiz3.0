package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaFreshStart
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntryState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp

/** Separate capability: ordinary navigation implementations cannot accidentally start an Arena attempt. */
interface ArenaRunNavigation {
    fun openArenaRunner(
        session: ArenaSessionStamp,
        ticketId: String,
        fresh: Boolean,
    )
}

internal fun arenaRunRequest(
    state: ArenaEntryUiState,
    session: ArenaSessionStamp,
    ticketId: String,
    fresh: Boolean,
): ArenaRunnerRequest? {
    val row = state.runEntryFor(session, ticketId) ?: return null
    val offered = if (fresh) row.canStartArenaRun() else row.canReadArenaRun()
    return if (offered) {
        ArenaRunnerRequest(
            session.ownerUid,
            ticketId,
            if (fresh) ArenaFreshStart(session, SessionMode.LEARNING) else null,
            initiatingSession = session,
        )
    } else {
        null
    }
}

private fun ArenaEntryUiState.runEntryFor(
    session: ArenaSessionStamp,
    ticketId: String,
): ArenaLocalEntry? {
    val row =
        takeIf { !busy && this.session == session }
            ?.tickets
            ?.singleOrNull { it.ticketId == ticketId }
    return row?.takeIf { it.header?.ownerUid == session.ownerUid && it.header?.ticketId == ticketId }
}

/** A button offer, not a lease. The Run repository still has to win the atomic local claim. */
internal fun ArenaLocalEntry.canStartArenaRun(): Boolean =
    state == ArenaLocalEntryState.PREPARED_SNAPSHOT && localDecision == ArenaLocalDecision.NONE &&
        header?.status == ArenaTicketStatus.RESERVED

internal fun ArenaLocalEntry.canReadArenaRun(): Boolean = header != null && state in READABLE_STATES

private val READABLE_STATES =
    setOf(
        ArenaLocalEntryState.RESUME_OR_RECONCILE,
        ArenaLocalEntryState.SETTLEMENT_PENDING,
        ArenaLocalEntryState.CANCEL_PENDING,
        ArenaLocalEntryState.TERMINAL_OBSERVED,
        ArenaLocalEntryState.NEEDS_RECOVERY,
    )
