package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/** Ephemeral continuations, deliberately without public fields or constructors. Never launch authority. */
interface ArenaEntriesCursor

interface ArenaPreparationsCursor

interface ArenaRecoveryCursor

data class ArenaEntriesPage(val entries: List<ArenaLocalEntry>, val nextCursor: ArenaEntriesCursor?)

data class ArenaPreparationsPage(
    val preparations: List<ArenaPendingPreparation>,
    val nextCursor: ArenaPreparationsCursor?,
)

/** An exclusive server cursor is not an atomic inventory or evidence that a missing ticket was revoked. */
data class ArenaRecoveryPage(val tickets: List<ArenaTicketHeader>, val nextCursor: ArenaRecoveryCursor?)

data class ArenaLocalEntry(
    val ticketId: String,
    val header: ArenaTicketHeader?,
    val state: ArenaLocalEntryState,
)

/** Summaries only: no snapshot, checkpoint, launcher lease, receipt ACK or wallet authority. */
enum class ArenaLocalEntryState {
    /** A validated bank is present locally. Only a separate atomic runner claim can authorize launch. */
    PREPARED_SNAPSHOT,

    /** Never means restart from question zero; a runner must validate a single current run view. */
    RESUME_OR_RECONCILE,
    CANCEL_PENDING,
    SETTLEMENT_PENDING,

    /** Persisted terminal observation, not new proof of completion or payment. */
    TERMINAL_OBSERVED,
    NEEDS_RECOVERY,
}

data class ArenaPendingPreparation(
    val requestId: String,
    val request: ArenaPrepareRequest?,
    val state: ArenaPreparationState,
    val reason: ArenaPreparationReason,
)

enum class ArenaPreparationState { PENDING, ATTENTION, NEEDS_RECOVERY }

enum class ArenaPreparationReason {
    RETRY_SAME_REQUEST,
    PRICE_CHANGED_REQUIRES_CONSENT,
    OTHER_ATTENTION,
    NEEDS_RECOVERY,
}

/** RECORDED is a durable command observation, not a launch, refund, score or completion claim. */
enum class ArenaEntryCommandOutcome { RECORDED, ALREADY_RECORDED, RETRY_SAME_REQUEST, PRICE_CHANGED_REQUIRES_CONSENT }

/** Only CANCELLED_RESOLVED represents the existing proof-bound exact queue-resolution transaction. */
sealed interface ArenaEntryResolution {
    data class StillReserved(val observation: ArenaTicketStatusObservation.Reserved) : ArenaEntryResolution

    data class SettledUnchanged(val observation: ArenaTicketStatusObservation.Settled) : ArenaEntryResolution

    data class CancelledResolved(val observation: ArenaTicketStatusObservation.Cancelled) : ArenaEntryResolution
}

/** Closed failure vocabulary; neither message nor cause contains storage, backend or answer payloads. */
enum class ArenaEntryFailureKind {
    ACCOUNT_UNAVAILABLE,
    ACCOUNT_CHANGED,
    NO_NETWORK,
    INVALID_REQUEST,
    CONFLICT,
    NEEDS_RECOVERY,
    UNAVAILABLE,
}

class ArenaEntryFailure(val kind: ArenaEntryFailureKind) : Exception(kind.name)
