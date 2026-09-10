package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.ArenaFrozenRatingDao
import com.tpov.schoolquiz.shared.core.persistence.ArenaFrozenRatingIntentEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaFrozenRatingProof
import com.tpov.schoolquiz.shared.core.persistence.ArenaFrozenRatingSave
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonRatingSubmittedLocalEntity
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.AttemptSettlementReceiptParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultEntityRef
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaFrozenRatingState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaFrozenRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ServerAttemptSettlementRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

/**
 * The arena route never resolves today's lesson hierarchy.  It derives its rating body only from
 * the retained SETTLE command and saves the first random id/body with the shared marker and queue.
 */
data class ArenaFrozenRatingDependencies(
    val dao: ArenaFrozenRatingDao,
    val receipts: MutationReceiptStore,
    val settlements: ServerAttemptSettlementRepository,
    val sessions: ArenaSessionProvider,
    val gate: ArenaOwnerGate,
    val ids: RatingIdProvider,
    val clock: Clock,
    val codec: ArenaJournalCodec,
    val settlementParser: AttemptSettlementReceiptParser = AttemptSettlementReceiptParser(),
)

class ArenaFrozenRatingRepositoryImpl(
    private val dependencies: ArenaFrozenRatingDependencies,
) : ArenaFrozenRatingRepository {
    private val dao get() = dependencies.dao
    private val receipts get() = dependencies.receipts
    private val settlements get() = dependencies.settlements
    private val sessions get() = dependencies.sessions
    private val gate get() = dependencies.gate
    private val ids get() = dependencies.ids
    private val clock get() = dependencies.clock
    private val codec get() = dependencies.codec
    private val settlementParser get() = dependencies.settlementParser

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Flow<ArenaFrozenRatingState> =
        flow {
            val source =
                source(stamp, ticketId) ?: run {
                    emit(ArenaFrozenRatingState.Unavailable)
                    return@flow
                }
            val local =
                dao.observeIntent(stamp.ownerUid, ticketId)
                    .combine(
                        dao.observeSharedMarker(stamp.ownerUid, source.lessonId),
                    ) { intent, marker -> intent to marker }
                    .flatMapLatest { (intent, marker) ->
                        if (intent == null) {
                            flowOf(
                                if (marker) {
                                    ArenaFrozenRatingState.ExistingUnverified
                                } else {
                                    ArenaFrozenRatingState.Pending
                                },
                            )
                        } else {
                            dao.observeOutbox(intent.mutationId).combine(
                                receipts.observeEntity(stamp.ownerUid, intent.entityRef),
                            ) { queued, rows ->
                                val rating = intent.ratingOrNull()
                                when {
                                    rating == null -> ArenaFrozenRatingState.ExistingUnverified
                                    rows.any {
                                        it.isAcceptedArenaRating(
                                            intent,
                                        )
                                    } -> ArenaFrozenRatingState.Accepted(rating)
                                    queued.matches(intent) -> ArenaFrozenRatingState.Queued(rating)
                                    else -> ArenaFrozenRatingState.ExistingUnverified
                                }
                            }
                        }
                    }
            emitAll(
                local.combine(settlements.observe(source.identity)) { rating, settlement ->
                    if (sessions.current() != stamp || !settlement.isConfirmedCompletion) {
                        ArenaFrozenRatingState.Unavailable
                    } else {
                        rating
                    }
                },
            )
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun submit(
        stamp: ArenaSessionStamp,
        ticketId: String,
        rating: Int,
    ): Result<ArenaFrozenRatingState> =
        try {
            Result.success(submitChecked(stamp, ticketId, rating))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    private suspend fun submitChecked(
        stamp: ArenaSessionStamp,
        ticketId: String,
        rating: Int,
    ): ArenaFrozenRatingState {
        require(rating in ARENA_RATING_RANGE)
        requireCurrent(stamp)
        val original = OutboxSession(stamp.ownerUid, stamp.generation)
        return retainedState(stamp, ticketId, original)
            ?: submitFreshRating(stamp, ticketId, rating, original)
    }

    private suspend fun retainedState(
        stamp: ArenaSessionStamp,
        ticketId: String,
        original: OutboxSession,
    ): ArenaFrozenRatingState? {
        val retained = gate.withOwner(original) { dao.intent(stamp.ownerUid, ticketId) }
        requireCurrent(stamp)
        return retained?.let { existingState(stamp, it) }
    }

    private suspend fun submitFreshRating(
        stamp: ArenaSessionStamp,
        ticketId: String,
        rating: Int,
        original: OutboxSession,
    ): ArenaFrozenRatingState {
        val source = source(stamp, ticketId)
        return when {
            source == null -> ArenaFrozenRatingState.Unavailable
            isBlocked(original, stamp, source) -> ArenaFrozenRatingState.ExistingUnverified
            else -> submitConfirmedRating(stamp, source, rating, original)
        }
    }

    private suspend fun submitConfirmedRating(
        stamp: ArenaSessionStamp,
        source: FrozenSource,
        rating: Int,
        original: OutboxSession,
    ): ArenaFrozenRatingState {
        val observed = settlements.observe(source.identity).first()
        requireCurrent(stamp)
        return if (observed.isConfirmedCompletion) {
            persistFirst(original, stamp, source, rating)
        } else {
            ArenaFrozenRatingState.Unavailable
        }
    }

    private suspend fun isBlocked(
        original: OutboxSession,
        stamp: ArenaSessionStamp,
        source: FrozenSource,
    ): Boolean {
        val blocked =
            gate.withOwner(original) {
                dao.intentForLesson(
                    stamp.ownerUid,
                    source.lessonId,
                ) != null || dao.hasSharedMarker(stamp.ownerUid, source.lessonId)
            }
        requireCurrent(stamp)
        return blocked
    }

    private suspend fun persistFirst(
        original: OutboxSession,
        stamp: ArenaSessionStamp,
        source: FrozenSource,
        rating: Int,
    ): ArenaFrozenRatingState {
        val saved = gate.withOwner(original) { saveUnderOwner(stamp, source, rating) }
        requireCurrent(stamp)
        return when (saved) {
            is ArenaFrozenRatingSave.Created -> ArenaFrozenRatingState.Queued(rating)
            is ArenaFrozenRatingSave.Retained -> existingState(stamp, saved.intent, saved.outbox)
            ArenaFrozenRatingSave.ExistingUnverified -> ArenaFrozenRatingState.ExistingUnverified
        }
    }

    private suspend fun saveUnderOwner(
        stamp: ArenaSessionStamp,
        source: FrozenSource,
        rating: Int,
    ): ArenaFrozenRatingSave {
        val settlementReceipt = receipts.find(stamp.ownerUid, source.settlementMutation)
        require(
            settlementReceipt != null &&
                settlementParser.parse(
                    settlementReceipt,
                    source.identity,
                ).isConfirmedCompletion,
        )
        requireCurrent(stamp)
        dao.intent(stamp.ownerUid, source.ticketId)?.let {
            return ArenaFrozenRatingSave.Retained(it, dao.outbox(it.mutationId))
        }
        val now = clock.now().toEpochMilliseconds()
        val ratingId = ids.provide(stamp.ownerUid, LessonId(source.lessonId)).value
        val intent = source.intent(ratingId, rating, now)
        return dao.saveFirst(
            marker = LessonRatingSubmittedLocalEntity(stamp.ownerUid, source.lessonId, now),
            intent = intent,
            outbox = source.outbox(intent),
            proof = source.proof(settlementReceipt),
        )
    }

    private suspend fun source(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): FrozenSource? {
        requireCurrent(stamp)
        val original = OutboxSession(stamp.ownerUid, stamp.generation)
        val source =
            gate.withOwner(original) {
                val ticket = dao.ticket(stamp.ownerUid, ticketId) ?: return@withOwner null
                val command = dao.settlementCommand(stamp.ownerUid, ticketId) ?: return@withOwner null
                FrozenSource.parse(ticket, command, codec)
            }
        requireCurrent(stamp)
        return source
    }

    private suspend fun existingState(
        stamp: ArenaSessionStamp,
        intent: ArenaFrozenRatingIntentEntity,
        queued: OutboxEntity? = null,
    ): ArenaFrozenRatingState {
        requireCurrent(stamp)
        val receipt = receipts.find(stamp.ownerUid, intent.mutationId)
        requireCurrent(stamp)
        val waiting = queued ?: dao.outbox(intent.mutationId)
        requireCurrent(stamp)
        val rating = intent.ratingOrNull() ?: return ArenaFrozenRatingState.ExistingUnverified
        return when {
            receipt != null && receipt.isAcceptedArenaRating(intent) -> ArenaFrozenRatingState.Accepted(rating)
            waiting.matches(intent) -> ArenaFrozenRatingState.Queued(rating)
            else -> ArenaFrozenRatingState.ExistingUnverified
        }
    }

    private fun requireCurrent(stamp: ArenaSessionStamp) {
        check(sessions.current() == stamp) { "Arena rating owner changed" }
    }
}

private data class FrozenSource(
    val ownerUid: String,
    val ticketId: String,
    val attemptId: String,
    val lessonId: String,
    val lessonVersion: Long,
    val catalogId: String,
    val questId: String,
    val sectionId: String,
    val themeId: String,
    val completedAtMs: Long,
    val difficulty: com.tpov.schoolquiz.shared.core.question_schema.Difficulty,
    val settlementBodyJson: String,
    val settlementBodySha256: String,
) {
    val identity: AttemptIdentity
        get() = AttemptIdentity(AttemptId(attemptId), ownerUid, LessonId(lessonId), lessonVersion, difficulty)
    val settlementMutation: String
        get() = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, attemptId)

    fun proof(receipt: MutationReceipt): ArenaFrozenRatingProof =
        ArenaFrozenRatingProof(
            ownerUid, ticketId, attemptId, settlementMutation, LessonResultEntityRef.attempt(attemptId),
            settlementBodyJson, settlementBodySha256, receipt.payloadJson, receipt.receivedAtMs,
        )

    fun intent(
        ratingId: String,
        rating: Int,
        now: Long,
    ): ArenaFrozenRatingIntentEntity {
        val payload = payload(ratingId, rating, now)
        return ArenaFrozenRatingIntentEntity(
            ownerUid, ticketId, attemptId, lessonId, ratingId,
            OutboxOperations.mutationKey(OutboxOperations.SUBMIT_RATING, ratingId),
            OutboxOperations.SUBMIT_RATING, payload, LessonResultEntityRef.rating(ratingId), now,
        )
    }

    fun outbox(intent: ArenaFrozenRatingIntentEntity) =
        OutboxEntity(
            mutationId = intent.mutationId, ownerUid = intent.ownerUid, operation = intent.operation,
            payload = intent.payload, entityRef = intent.entityRef, expectedVersion = null, state = "WAITING",
            attemptCount = 0, nextRetryAtMs = 0, lastError = null, createdAtMs = intent.createdAtMs,
        )

    private fun payload(
        ratingId: String,
        rating: Int,
        now: Long,
    ): String =
        buildJsonObject {
            put("ratingId", ratingId)
            put("ticketId", ticketId)
            put("attemptId", attemptId)
            put("userId", ownerUid)
            put("scope", "public")
            put("ownerUid", kotlinx.serialization.json.JsonNull)
            put("catalogId", catalogId)
            put("questId", questId)
            put("sectionId", sectionId)
            put("themeId", themeId)
            put("lessonId", lessonId)
            put("lessonVersion", lessonVersion)
            put("sourceShelf", "arena")
            put("rating", rating)
            put("ratedAtMs", now)
            put("createdAtMs", now)
        }.toString()

    companion object {
        fun parse(
            ticket: ArenaTicketRecordEntity,
            command: com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity,
            codec: ArenaJournalCodec,
        ): FrozenSource? =
            runCatching {
                require(codec.isIntact(command))
                require(ticket.ownerUid == command.ownerUid && ticket.ticketId == command.ticketId)
                require(
                    command.commandKind == "SETTLE" &&
                        command.commandId == ticket.attemptId &&
                        command.attemptId == ticket.attemptId,
                )
                val body = Json.parseToJsonElement(command.bodyJson).jsonObject
                require(body.text("ownerUid") == ticket.ownerUid && body.text("userId") == ticket.ownerUid)
                require(body.text("ticketId") == ticket.ticketId && body.text("attemptId") == ticket.attemptId)
                require(
                    body.text("scope") == "public" &&
                        body.text("sourceShelf") == "arena" &&
                        body.text("completionKind") == "COMPLETED",
                )
                FrozenSource(
                    ownerUid = ticket.ownerUid, ticketId = ticket.ticketId, attemptId = ticket.attemptId,
                    lessonId = body.requiredText("lessonId"), lessonVersion = body.requiredLong("lessonVersion"),
                    catalogId = body.requiredText("catalogId"), questId = body.requiredText("questId"),
                    sectionId = body.requiredText("sectionId"), themeId = body.requiredText("themeId"),
                    completedAtMs = body.requiredLong("completedAtMs"),
                    difficulty =
                        com.tpov.schoolquiz.shared.core.question_schema.Difficulty.valueOf(
                            body.requiredText("difficulty"),
                        ),
                    settlementBodyJson = command.bodyJson, settlementBodySha256 = command.bodySha256,
                )
            }.getOrNull()
    }
}

private fun ArenaFrozenRatingIntentEntity.ratingOrNull(): Int? =
    runCatching {
        (Json.parseToJsonElement(payload).jsonObject["rating"] as? JsonPrimitive)?.content?.toIntOrNull()
    }
        .getOrNull()?.takeIf { it in ARENA_RATING_RANGE }

private const val MINIMUM_ARENA_RATING = 1
private const val MAXIMUM_ARENA_RATING = 3
private val ARENA_RATING_RANGE = MINIMUM_ARENA_RATING..MAXIMUM_ARENA_RATING

private fun OutboxEntity?.matches(intent: ArenaFrozenRatingIntentEntity): Boolean =
    this != null &&
        mutationId == intent.mutationId &&
        ownerUid == intent.ownerUid &&
        operation == intent.operation &&
        payload == intent.payload &&
        entityRef == intent.entityRef &&
        expectedVersion == null &&
        state in setOf(OutboxState.WAITING.name, OutboxState.WAITING_PRECONDITION.name)

internal fun MutationReceipt.isAcceptedArenaRating(intent: ArenaFrozenRatingIntentEntity): Boolean =
    runCatching {
        require(
            ownerUid == intent.ownerUid &&
                mutationId == intent.mutationId &&
                operation == OutboxOperations.SUBMIT_RATING,
        )
        require(entityRef == intent.entityRef)
        val body = Json.parseToJsonElement(payloadJson).jsonObject
        val receipt = body.matchingArenaRatingReceipt(intent) ?: return@runCatching false
        require(receipt.requiredLong("schemaVersion") == ARENA_SERVICE_RECEIPT_SCHEMA)
        require(receipt.text("status") == "APPLIED" && receipt.text("operation") == "RATE_LESSON")
        val result = receipt["result"] as? JsonObject ?: return@runCatching false
        require(
            result.text("lessonId") == intent.lessonId &&
                result.text("ratingId")?.startsWith("arena-rating:") == true,
        )
        require(result.requiredLong("acceptedAtMs") >= 0)
        true
    }.getOrDefault(false)

/** The queue persists complete submitMutation callable data, not the nested arena-service row. */
private fun JsonObject.matchingArenaRatingReceipt(intent: ArenaFrozenRatingIntentEntity): JsonObject? {
    require(boolean("replayed") != null)
    val result = checkNotNull(this["result"] as? JsonObject)
    require(result.requiredLong("accepted") == SINGLE_QUEUED_RATING)
    val receipts = checkNotNull(result["receipts"] as? JsonArray)
    return receipts.filterIsInstance<JsonObject>()
        .filter { it.text("uid") == intent.ownerUid && it.text("mutationId") == intent.ratingId }
        .singleOrNull()
}

private const val ARENA_SERVICE_RECEIPT_SCHEMA = 1L
private const val SINGLE_QUEUED_RATING = 1L

private fun JsonObject.boolean(name: String): Boolean? =
    when ((this[name] as? JsonPrimitive)?.takeUnless { it.isString }?.content) {
        "true" -> true
        "false" -> false
        else -> null
    }

private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.requiredText(name: String): String = checkNotNull(text(name)).also { require(it.isNotBlank()) }

private fun JsonObject.requiredLong(name: String): Long =
    checkNotNull((this[name] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull).also { require(it >= 0) }
