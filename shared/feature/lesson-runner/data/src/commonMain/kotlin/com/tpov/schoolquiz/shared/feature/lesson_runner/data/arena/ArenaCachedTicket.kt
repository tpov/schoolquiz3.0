package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot

/** No Flow/auto-load/UI in this slice. Consumers keep their captured owner epoch with these values. */
sealed interface ArenaCachedTicket {
    data class Available(val snapshot: ArenaTicketSnapshot) : ArenaCachedTicket

    data class ResumeOrReconcile(val snapshot: ArenaTicketSnapshot) : ArenaCachedTicket

    data class DecisionPending(val header: ArenaTicketHeader, val decision: String) : ArenaCachedTicket

    data class Terminal(val header: ArenaTicketHeader) : ArenaCachedTicket

    data class SnapshotUnavailable(val header: ArenaTicketHeader) : ArenaCachedTicket
}

/** A launch claim returns the original frozen snapshot/attempt ID, never a regenerated attempt. */
sealed interface ArenaLaunchClaim {
    data class Claimed(val snapshot: ArenaTicketSnapshot) : ArenaLaunchClaim

    data class NotFresh(val state: ArenaCachedTicket) : ArenaLaunchClaim
}

enum class ArenaCommandDispatch { RECORDED, ALREADY_RECORDED, RETRY_REGEN, PRICE_CHANGED }

class ArenaJournalAttention(val reason: String) : IllegalStateException(reason)
