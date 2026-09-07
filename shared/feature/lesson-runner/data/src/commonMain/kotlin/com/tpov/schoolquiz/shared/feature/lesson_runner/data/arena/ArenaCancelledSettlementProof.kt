package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.persistence.ARENA_RESOLVED_CANCELLED
import com.tpov.schoolquiz.shared.core.persistence.ArenaCancellationValidator
import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptIntentEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusMetadata
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusObservation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Required production proof factory. No mutable last response, bank access or permissive validator. */
class ArenaCancelledSettlementProof(
    private val codec: ArenaJournalCodec,
    sha256: (String) -> String,
) {
    private val parser = ArenaTicketWireParser(sha256)

    internal fun firstIntent(
        ticket: ArenaTicketRecordEntity,
        command: ArenaCommandEntity,
    ): LessonAttemptIntentEntity {
        val header = codec.header(ticket)
        require(codec.record(header, 0).termsJson == ticket.termsJson)
        require(ticket.localUse == "LAUNCHED" && ticket.decision == "SETTLE")
        require(command.ownerUid == ticket.ownerUid && command.ticketId == ticket.ticketId)
        require(command.attemptId == ticket.attemptId && command.commandId == ticket.attemptId)
        require(command.state in setOf("PENDING", "ATTENTION", ARENA_RESOLVED_CANCELLED))
        require(command.state != ARENA_RESOLVED_CANCELLED || ticket.serverStatus == "CANCELLED")
        val row = codec.outbox(command) // Verifies bounded retained body SHA; this never inserts a delivery.
        val body = Json.parseToJsonElement(command.bodyJson).jsonObject
        require(body.toString() == command.bodyJson) { "Noncanonical retained body" }
        val contextFields = body.keys.intersect(CONTEXT_FIELDS)
        require(contextFields.isEmpty() || contextFields == CONTEXT_FIELDS)
        require(body.keys == BODY_FIELDS + contextFields)
        require(body["ownerUid"] == JsonPrimitive(ticket.ownerUid) && body["userId"] == body["ownerUid"])
        require(
            body["ticketId"] == JsonPrimitive(ticket.ticketId) && body["attemptId"] == JsonPrimitive(ticket.attemptId),
        )
        require(body["lessonId"] == JsonPrimitive(header.lessonId))
        require(body["lessonVersion"] == JsonPrimitive(header.lessonVersion))
        require(body["difficulty"] == JsonPrimitive(header.difficulty.name))
        require(body["scope"] == JsonPrimitive("public") && body["sourceShelf"] == JsonPrimitive("arena"))
        require(body["completionKind"] == JsonPrimitive("COMPLETED"))
        require(command.createdAtMs >= 0 && command.createdAtMs <= MAX_SAFE_INTEGER)
        require(body["completedAtMs"] == JsonPrimitive(command.createdAtMs))
        require(body["createdAtMs"] == JsonPrimitive(command.createdAtMs))
        require((body["chargeClaims"] as? JsonPrimitive)?.isString == true)
        require(body["answers"] is JsonArray && body["served"] is JsonArray)
        return LessonAttemptIntentEntity(
            ticket.attemptId,
            row.ownerUid,
            row.mutationId,
            row.operation,
            row.payload,
            row.entityRef,
            row.expectedVersion,
        )
    }

    internal fun bind(
        response: ArenaTicketStatusObservation,
        request: ArenaTicketGetRequest,
        originalTicket: ArenaTicketRecordEntity,
        originalCommand: ArenaCommandEntity,
        observedAtMs: Long,
    ): Bound {
        require(observedAtMs >= 0)
        val observation = canonicalRecord(response, observedAtMs)
        val wire =
            buildJsonObject {
                put("ticket", statusTicket(observation, response.metadata))
            }.toString()
        val verified = parser.status(wire, request) // Typed models supplied by a fake are not proof by themselves.
        require(verified == response)
        return BoundProof(this, originalTicket, originalCommand, verified, observation).also {
            it.checkCurrent(originalTicket, originalCommand)
        }
    }

    private fun canonicalRecord(
        response: ArenaTicketStatusObservation,
        observedAtMs: Long,
    ): ArenaTicketRecordEntity {
        // Always start from a new header-only record: never retain an unsafe old snapshot/checkpoint.
        val row = codec.record(response.metadata.header, observedAtMs)
        return when (response) {
            is ArenaTicketStatusObservation.Reserved -> row
            is ArenaTicketStatusObservation.Cancelled ->
                codec.terminal(row, ArenaCancelResponse.Cancelled(row.ticketId, response.receipt), observedAtMs)
            is ArenaTicketStatusObservation.Settled ->
                codec.terminal(row, ArenaCancelResponse.AlreadySettled(row.ticketId, response.receipt), observedAtMs)
        }
    }

    private fun statusTicket(
        row: ArenaTicketRecordEntity,
        metadata: ArenaTicketStatusMetadata,
    ): JsonObject =
        JsonObject(
            Json.parseToJsonElement(row.termsJson).jsonObject +
                buildJsonObject {
                    put("status", row.serverStatus)
                    put("authorUid", metadata.authorUid)
                    put("ratingVersion", metadata.ratingVersion)
                    put("contentContext", context(metadata) ?: JsonNull)
                    put("terminal", row.terminalJson?.let(Json::parseToJsonElement) ?: JsonNull)
                },
        )

    /** One immutable server observation per invocation; all fallible proof checks run before DAO writes. */
    internal interface Bound : ArenaCancellationValidator {
        val response: ArenaTicketStatusObservation
        val observation: ArenaTicketRecordEntity
        val firstIntent: LessonAttemptIntentEntity

        fun checkCurrent(
            ticket: ArenaTicketRecordEntity,
            command: ArenaCommandEntity,
        )
    }

    private class BoundProof(
        private val factory: ArenaCancelledSettlementProof,
        private val originalTicket: ArenaTicketRecordEntity,
        private val originalCommand: ArenaCommandEntity,
        override val response: ArenaTicketStatusObservation,
        override val observation: ArenaTicketRecordEntity,
    ) : Bound {
        override val firstIntent = factory.firstIntent(originalTicket, originalCommand)

        override fun checkCurrent(
            ticket: ArenaTicketRecordEntity,
            command: ArenaCommandEntity,
        ) {
            require(factory.firstIntent(ticket, command) == firstIntent)
            require(
                command.copy(
                    state = originalCommand.state,
                    lastFailure = originalCommand.lastFailure,
                ) == originalCommand,
            )
            require(ticket.ownerUid == observation.ownerUid && ticket.ticketId == observation.ticketId)
            require(ticket.attemptId == observation.attemptId && ticket.requestId == observation.requestId)
            require(ticket.termsJson == originalTicket.termsJson && ticket.termsJson == observation.termsJson)
            checkContext(command, response.metadata)
            when (ticket.serverStatus) {
                "RESERVED" -> require(ticket.terminalJson == null)
                "CANCELLED", "SETTLED" -> {
                    require(ticket.serverStatus == observation.serverStatus)
                    require(ticket.terminalJson == observation.terminalJson)
                }
                else -> error("Unknown retained terminal state")
            }
        }

        override fun validate(
            ticket: ArenaTicketRecordEntity,
            command: ArenaCommandEntity,
            firstIntent: LessonAttemptIntentEntity,
            observation: ArenaTicketRecordEntity,
        ) {
            require(response is ArenaTicketStatusObservation.Cancelled)
            require(this.observation == observation && this.firstIntent == firstIntent)
            require(factory.canonicalRecord(response, observation.observedAtMs) == observation)
            checkCurrent(ticket, command)
        }
    }

    private companion object {
        val CONTEXT_FIELDS = setOf("catalogId", "questId", "sectionId", "themeId")
        val BODY_FIELDS =
            setOf(
                "ownerUid", "userId", "ticketId", "attemptId", "lessonId", "lessonVersion", "difficulty",
                "scope", "sourceShelf", "completionKind", "completedAtMs", "createdAtMs",
                "chargeClaims", "answers", "served",
            )
        const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

        fun context(metadata: ArenaTicketStatusMetadata): JsonObject? =
            metadata.contentContext?.let {
                buildJsonObject {
                    put("catalogId", it.catalogId)
                    put("questId", it.questId)
                    put("sectionId", it.sectionId)
                    put("themeId", it.themeId)
                }
            }

        fun checkContext(
            command: ArenaCommandEntity,
            metadata: ArenaTicketStatusMetadata,
        ) {
            val body = Json.parseToJsonElement(command.bodyJson).jsonObject
            val retained = JsonObject(body.filterKeys { it in CONTEXT_FIELDS })
            require(retained == (context(metadata) ?: JsonObject(emptyMap())))
        }
    }
}
