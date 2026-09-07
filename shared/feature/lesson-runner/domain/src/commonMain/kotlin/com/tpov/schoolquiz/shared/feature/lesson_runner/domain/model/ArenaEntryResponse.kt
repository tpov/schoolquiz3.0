package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

sealed interface ArenaPrepareResponse {
    /** An idempotent replay may already be Cancelled or Settled. Do not treat ISSUED as RESERVED. */
    data class Ticket(val ticket: ArenaEntryTicket) : ArenaPrepareResponse

    data class RetryRegeneration(val requestId: String) : ArenaPrepareResponse

    /** No new hold was made. Requires another explicit price confirmation, not an automatic retry ceiling. */
    data object PriceChanged : ArenaPrepareResponse
}

sealed interface ArenaCancelResponse {
    data class Cancelled(val ticketId: String, val receipt: ArenaCancellationReceipt) : ArenaCancelResponse

    data class AlreadySettled(val ticketId: String, val receipt: ArenaSettlementReceipt) : ArenaCancelResponse

    data class RetryRegeneration(val ticketId: String, val requestId: String) : ArenaCancelResponse
}

/** Boundary failures never imply cancellation, release of a server hold, or permission to issue again. */
enum class ArenaRemoteFailureKind { ACCOUNT_UNAVAILABLE, ACCOUNT_CHANGED, MALFORMED_RESPONSE }

class ArenaRemoteFailure(val kind: ArenaRemoteFailureKind) : Exception(kind.name)
