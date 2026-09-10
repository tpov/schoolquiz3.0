package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import kotlinx.coroutines.flow.Flow

/** Small aggregate for a bankless arena rating; it intentionally does not grow ArenaTicketDao. */
@Dao
abstract class ArenaFrozenRatingDao {
    @Query("SELECT * FROM arena_ticket_records WHERE owner_uid = :owner AND ticket_id = :ticket")
    abstract suspend fun ticket(
        owner: String,
        ticket: String,
    ): ArenaTicketRecordEntity?

    @Query(
        "SELECT * FROM arena_command_journal WHERE owner_uid = :owner AND ticket_id = :ticket " +
            "AND command_kind = 'SETTLE'",
    )
    abstract suspend fun settlementCommand(
        owner: String,
        ticket: String,
    ): ArenaCommandEntity?

    @Query("SELECT * FROM arena_frozen_rating_intents WHERE owner_uid = :owner AND ticket_id = :ticket")
    abstract suspend fun intent(
        owner: String,
        ticket: String,
    ): ArenaFrozenRatingIntentEntity?

    @Query("SELECT * FROM arena_frozen_rating_intents WHERE owner_uid = :owner AND lesson_id = :lesson LIMIT 1")
    abstract suspend fun intentForLesson(
        owner: String,
        lesson: String,
    ): ArenaFrozenRatingIntentEntity?

    @Query("SELECT * FROM arena_frozen_rating_intents WHERE owner_uid = :owner AND ticket_id = :ticket")
    abstract fun observeIntent(
        owner: String,
        ticket: String,
    ): Flow<ArenaFrozenRatingIntentEntity?>

    @Query("SELECT COUNT(*) > 0 FROM lesson_rating_submitted_local WHERE user_id = :owner AND lesson_id = :lesson")
    abstract fun observeSharedMarker(
        owner: String,
        lesson: String,
    ): Flow<Boolean>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM lesson_rating_submitted_local " +
            "WHERE user_id = :owner AND lesson_id = :lesson)",
    )
    abstract suspend fun hasSharedMarker(
        owner: String,
        lesson: String,
    ): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSharedMarker(row: LessonRatingSubmittedLocalEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertIntent(row: ArenaFrozenRatingIntentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertOutbox(row: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE mutation_id = :mutation")
    abstract suspend fun outbox(mutation: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE mutation_id = :mutation")
    abstract fun observeOutbox(mutation: String): Flow<OutboxEntity?>

    @Query("SELECT * FROM mutation_receipts WHERE owner_uid = :owner AND mutation_id = :mutation")
    abstract suspend fun receipt(
        owner: String,
        mutation: String,
    ): MutationReceiptEntity?

    /**
     * Create the marker, retained intent and queued request together.  An existing intent always
     * wins before a new random id can be proposed.  A marker for another ticket/ordinary route is
     * not overwritten: callers surface it as ExistingUnverified instead.
     */
    @Transaction
    open suspend fun saveFirst(
        marker: LessonRatingSubmittedLocalEntity,
        intent: ArenaFrozenRatingIntentEntity,
        outbox: OutboxEntity,
        proof: ArenaFrozenRatingProof,
    ): ArenaFrozenRatingSave {
        requireCandidate(marker, intent, proof)
        requireCurrentProof(proof)
        return existingSave(intent, proof) ?: createFirst(marker, intent, outbox)
    }

    /**
     * A terminal old queue record may only remove the exact intent it quarantined.  It returns the
     * marker only when that delete happened, so a stale caller cannot erase a newer shared marker.
     */
    @Transaction
    open suspend fun rollbackExact(
        owner: String,
        ticket: String,
        mutation: String,
        ratingId: String,
        payload: String,
    ): Boolean =
        rollbackTarget(owner, ticket, mutation, ratingId, payload)?.let { current ->
            deleteExact(owner, ticket, mutation, ratingId, payload)
            deleteExactMarker(owner, current.lessonId, current.createdAtMs)
            true
        } ?: false

    @Query(
        "SELECT EXISTS(SELECT 1 FROM lesson_rating_submitted_local WHERE user_id = :owner " +
            "AND lesson_id = :lesson AND submitted_at = :submittedAt)",
    )
    abstract suspend fun hasExactMarker(
        owner: String,
        lesson: String,
        submittedAt: Long,
    ): Boolean

    @Query(
        "DELETE FROM lesson_rating_submitted_local WHERE user_id = :owner AND lesson_id = :lesson " +
            "AND submitted_at = :submittedAt",
    )
    abstract suspend fun deleteExactMarker(
        owner: String,
        lesson: String,
        submittedAt: Long,
    ): Int

    @Query(
        "DELETE FROM arena_frozen_rating_intents WHERE owner_uid = :owner AND ticket_id = :ticket " +
            "AND mutation_id = :mutation AND rating_id = :ratingId AND payload = :payload",
    )
    abstract suspend fun deleteExact(
        owner: String,
        ticket: String,
        mutation: String,
        ratingId: String,
        payload: String,
    ): Int

    private fun requireCandidate(
        marker: LessonRatingSubmittedLocalEntity,
        intent: ArenaFrozenRatingIntentEntity,
        proof: ArenaFrozenRatingProof,
    ) {
        require(marker.userId == intent.ownerUid && marker.lessonId == intent.lessonId)
        require(marker.submittedAt == intent.createdAtMs)
        require(proof.ownerUid == intent.ownerUid && proof.ticketId == intent.ticketId)
        require(proof.attemptId == intent.attemptId)
    }

    private suspend fun requireCurrentProof(proof: ArenaFrozenRatingProof) {
        val ticket = ticket(proof.ownerUid, proof.ticketId)
        val command = settlementCommand(proof.ownerUid, proof.ticketId)
        val settlement = receipt(proof.ownerUid, proof.settlementMutation)
        require(ticket?.attemptId == proof.attemptId && ticket.ownerUid == proof.ownerUid)
        require(command?.commandId == proof.attemptId && command.attemptId == proof.attemptId)
        require(command.bodyJson == proof.settlementBodyJson && command.bodySha256 == proof.settlementBodySha256)
        require(settlement?.ownerUid == proof.ownerUid && settlement.mutationId == proof.settlementMutation)
        require(
            settlement.operation == OutboxOperations.SUBMIT_ATTEMPT &&
                settlement.entityRef == proof.settlementEntityRef,
        )
        require(
            settlement.payloadJson == proof.settlementPayloadJson &&
                settlement.receivedAtMs == proof.settlementReceivedAtMs,
        )
    }

    private suspend fun existingSave(
        proposed: ArenaFrozenRatingIntentEntity,
        proof: ArenaFrozenRatingProof,
    ): ArenaFrozenRatingSave? {
        val retained = intent(proposed.ownerUid, proposed.ticketId)
        if (retained != null) {
            require(retained.ownerUid == proposed.ownerUid && retained.ticketId == proposed.ticketId)
            require(retained.attemptId == proof.attemptId && retained.lessonId == proposed.lessonId)
            return ArenaFrozenRatingSave.Retained(retained, outbox(retained.mutationId))
        }
        return if (intentForLesson(proposed.ownerUid, proposed.lessonId) != null ||
            hasSharedMarker(proposed.ownerUid, proposed.lessonId)
        ) {
            ArenaFrozenRatingSave.ExistingUnverified
        } else {
            null
        }
    }

    private suspend fun createFirst(
        marker: LessonRatingSubmittedLocalEntity,
        intent: ArenaFrozenRatingIntentEntity,
        outbox: OutboxEntity,
    ): ArenaFrozenRatingSave {
        require(outbox.mutationId == intent.mutationId && outbox.ownerUid == intent.ownerUid)
        require(outbox.operation == OutboxOperations.SUBMIT_RATING && outbox.payload == intent.payload)
        require(outbox.entityRef == intent.entityRef && outbox.expectedVersion == null && outbox.state == "WAITING")
        return if (insertSharedMarker(marker) == OUTBOX_ROW_IGNORED) {
            ArenaFrozenRatingSave.ExistingUnverified
        } else {
            insertIntent(intent)
            insertOutbox(outbox)
            ArenaFrozenRatingSave.Created(intent)
        }
    }

    private suspend fun rollbackTarget(
        owner: String,
        ticket: String,
        mutation: String,
        ratingId: String,
        payload: String,
    ): ArenaFrozenRatingIntentEntity? {
        val current = intent(owner, ticket)
        return current?.takeIf { it.matches(mutation, ratingId, payload) }
            ?.takeIf { hasExactMarker(owner, it.lessonId, it.createdAtMs) }
    }
}

private fun ArenaFrozenRatingIntentEntity.matches(
    mutation: String,
    ratingId: String,
    payload: String,
): Boolean = mutationId == mutation && this.ratingId == ratingId && this.payload == payload

data class ArenaFrozenRatingProof(
    val ownerUid: String,
    val ticketId: String,
    val attemptId: String,
    val settlementMutation: String,
    val settlementEntityRef: String,
    val settlementBodyJson: String,
    val settlementBodySha256: String,
    val settlementPayloadJson: String,
    val settlementReceivedAtMs: Long,
)

sealed interface ArenaFrozenRatingSave {
    data class Retained(val intent: ArenaFrozenRatingIntentEntity, val outbox: OutboxEntity?) : ArenaFrozenRatingSave

    data class Created(val intent: ArenaFrozenRatingIntentEntity) : ArenaFrozenRatingSave

    data object ExistingUnverified : ArenaFrozenRatingSave
}
