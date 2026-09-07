package com.tpov.schoolquiz.shared.core.persistence

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations

/**
 * Required pure, synchronous proof validation, invoked inside the Room transaction against re-read rows.
 * The implementation must strictly validate the owner-bound server cancellation receipt, immutable header,
 * held amounts/conservation and timestamps, plus the retained body's hash and serialized identity.
 * A status string, generic delivery ACK or a caller's cancellation request is not server proof.
 *
 * No default implementation is provided. The future caller must hold OutboxArenaOwnerGate for the same
 * captured owner generation, excluding delivery passes and account cleanup. This is not multiprocess auth.
 */
fun interface ArenaCancellationValidator {
    fun validate(
        ticket: ArenaTicketRecordEntity,
        command: ArenaCommandEntity,
        firstIntent: LessonAttemptIntentEntity,
        observation: ArenaTicketRecordEntity,
    )
}

/** Retained SETTLE body is never reinterpreted as a local CANCEL command or successful attempt. */
const val ARENA_RESOLVED_CANCELLED = "RESOLVED_CANCELLED"

internal fun validateArenaCancellationIdentity(
    ticket: ArenaTicketRecordEntity,
    retained: ArenaCommandEntity,
    command: ArenaCommandEntity,
    firstIntent: LessonAttemptIntentEntity,
    observation: ArenaTicketRecordEntity,
) {
    if (ticket.decision != "SETTLE" || ticket.localUse != "LAUNCHED") throw ArenaJournalConflict()
    if (!retained.sameIntent(command) || retained.createdAtMs != command.createdAtMs) throw ArenaJournalConflict()
    validateCancellationCommand(ticket, retained)
    validateCancellationIntent(ticket, retained, firstIntent)
    validateCancellationObservation(ticket, observation)
}

private fun validateCancellationCommand(
    ticket: ArenaTicketRecordEntity,
    command: ArenaCommandEntity,
) {
    if (command.ownerUid != ticket.ownerUid || command.ticketId != ticket.ticketId || command.commandKind != "SETTLE") {
        throw ArenaJournalConflict()
    }
    val sameAttempt = command.commandId == ticket.attemptId && command.attemptId == ticket.attemptId
    val validState = command.state in setOf("PENDING", "ATTENTION", ARENA_RESOLVED_CANCELLED)
    if (!sameAttempt || !validState) throw ArenaJournalConflict()
}

private fun validateCancellationIntent(
    ticket: ArenaTicketRecordEntity,
    command: ArenaCommandEntity,
    intent: LessonAttemptIntentEntity,
) {
    val sameOwnerAttempt = intent.ownerUid == ticket.ownerUid && intent.attemptId == ticket.attemptId
    val sameOperationBody = intent.operation == OutboxOperations.SUBMIT_ATTEMPT && intent.payload == command.bodyJson
    if (!sameOwnerAttempt || !sameOperationBody) throw ArenaJournalConflict()
    if (intent.mutationId != OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, ticket.attemptId) ||
        intent.entityRef != "lesson_runner:attempt:${ticket.attemptId}" || intent.expectedVersion != null
    ) {
        throw ArenaJournalConflict()
    }
}

private fun validateCancellationObservation(
    ticket: ArenaTicketRecordEntity,
    observation: ArenaTicketRecordEntity,
) {
    val sameIdentity =
        observation.ownerUid == ticket.ownerUid && observation.ticketId == ticket.ticketId &&
            observation.attemptId == ticket.attemptId && observation.requestId == ticket.requestId
    if (!sameIdentity || observation.termsJson != ticket.termsJson) throw ArenaJournalConflict()
    validateCancellationEnvelope(observation)
    validateCancellationTerminal(ticket, observation)
}

private fun validateCancellationEnvelope(observation: ArenaTicketRecordEntity) {
    val headerOnly = observation.snapshotJson == null && observation.snapshotSha256 == null
    val noLocalState =
        observation.localUse == "UNUSED" && observation.decision == "NONE" &&
            observation.checkpointJson == null && observation.checkpointRevision == 0L
    if (!headerOnly || !noLocalState || observation.observedAtMs < 0) throw ArenaJournalConflict()
}

private fun validateCancellationTerminal(
    ticket: ArenaTicketRecordEntity,
    observation: ArenaTicketRecordEntity,
) {
    if (observation.serverStatus != "CANCELLED" || observation.terminalJson.isNullOrBlank()) {
        throw ArenaJournalConflict()
    }
    val compatible =
        when (ticket.serverStatus) {
            "RESERVED" -> ticket.terminalJson == null
            "CANCELLED" -> ticket.terminalJson == observation.terminalJson
            else -> false
        }
    if (!compatible) throw ArenaJournalConflict("Contradictory server terminal states")
}

internal fun validateArenaCancellationReplay(
    ticket: ArenaTicketRecordEntity,
    command: ArenaCommandEntity,
    pending: OutboxEntity?,
): Boolean {
    if (command.state != ARENA_RESOLVED_CANCELLED) return false
    if (ticket.serverStatus != "CANCELLED" || pending != null) throw ArenaJournalConflict()
    return true // Even a first-intent marker erased by later account cleanup must not be recreated on replay.
}
