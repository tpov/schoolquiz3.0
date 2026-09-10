package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryTicket
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketListRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptCompletionKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UnscoredTerminalIntent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Explicit public projection; stored terms never contain a status, token, private key or balance. */
class ArenaJournalCodec(private val sha256: (String) -> String) {
    private val parser = ArenaTicketWireParser(sha256)
    private val answerJson = Json { encodeDefaults = true }

    /** A retained command is usable as frozen provenance only when its stored bytes still hash. */
    fun isIntact(command: ArenaCommandEntity): Boolean =
        runCatching {
            verify(command)
            true
        }.getOrDefault(false)

    fun consent(
        request: ArenaPrepareRequest,
        now: Long,
    ): ArenaCommandEntity {
        val body =
            buildJsonObject {
                put("journalVersion", 1)
                put("expectedOwnerUid", request.ownerUid)
                put("requestId", request.requestId)
                put("difficulty", request.difficulty.name)
                when (request) {
                    is ArenaPrepareRequest.Random -> put("mode", "RANDOM")
                    is ArenaPrepareRequest.Selected -> {
                        put("mode", "SELECTED")
                        put("lessonId", request.lessonId)
                        put("maxPriceNolics", request.maxPriceNolics)
                    }
                }
            }.toString()
        return command(request.ownerUid, "PREPARE", request.requestId, null, null, body, now)
    }

    fun prepare(cmd: ArenaCommandEntity): ArenaPrepareRequest {
        require(cmd.commandKind == "PREPARE")
        verify(cmd)
        val body = json(cmd.bodyJson)
        val owner = body.text("expectedOwnerUid")
        val id = body.text("requestId")
        val difficulty = Difficulty.valueOf(body.text("difficulty"))
        val request =
            when (body.text("mode")) {
                "RANDOM" -> ArenaPrepareRequest.Random(owner, id, difficulty)
                "SELECTED" ->
                    ArenaPrepareRequest.Selected(
                        owner,
                        id,
                        difficulty,
                        body.text("lessonId"),
                        body.getValue("maxPriceNolics").jsonPrimitive.int,
                    )
                else -> error("Unknown immutable consent mode")
            }
        // Exact locally-versioned encoding also rejects unknown fields or changed numeric types.
        require(consent(request, cmd.createdAtMs).bodyJson == cmd.bodyJson)
        require(owner == cmd.ownerUid && id == cmd.commandId)
        return request
    }

    fun cancel(
        row: ArenaTicketRecordEntity,
        requestId: String,
        now: Long,
    ): ArenaCommandEntity {
        com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketCancelRequest(header(row), requestId)
        val body =
            buildJsonObject {
                put("expectedOwnerUid", row.ownerUid)
                put("ticketId", row.ticketId)
                put("requestId", requestId)
            }.toString()
        return command(row.ownerUid, "CANCEL", requestId, row.ticketId, row.attemptId, body, now)
    }

    fun cancellationRequest(
        row: ArenaTicketRecordEntity,
        cmd: ArenaCommandEntity,
    ) = com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketCancelRequest(
        header(row),
        cmd.commandId,
    ).also {
        verify(cmd)
        require(cancel(row, cmd.commandId, cmd.createdAtMs).bodyJson == cmd.bodyJson)
        require(cmd.ownerUid == row.ownerUid && cmd.ticketId == row.ticketId && cmd.attemptId == row.attemptId)
    }

    fun record(
        header: ArenaTicketHeader,
        now: Long,
    ): ArenaTicketRecordEntity {
        val row =
            ArenaTicketRecordEntity(
                header.ownerUid, header.ticketId, header.attemptId, header.requestId, terms(header).toString(),
                null, null, header.status.name, null, "UNUSED", "NONE", now,
            )
        require(this.header(row) == header)
        return row
    }

    fun record(
        ticket: ArenaEntryTicket,
        now: Long,
    ): ArenaTicketRecordEntity {
        val snapshot = snapshot(ticket.snapshot).toString()
        val row =
            record(ticket.snapshot.header, now).copy(
                snapshotJson = snapshot,
                snapshotSha256 = sha256(snapshot),
                terminalJson =
                    when (ticket) {
                        is ArenaEntryTicket.Reserved -> null
                        is ArenaEntryTicket.Cancelled -> cancellation(ticket.receipt).toString()
                        is ArenaEntryTicket.Settled -> settlement(ticket.receipt).toString()
                    },
            )
        decode(row) // Revalidate a fake/caller-built model with exactly the same strict wire parser.
        return row
    }

    fun preparedRecord(
        ticket: ArenaEntryTicket,
        request: ArenaPrepareRequest,
        now: Long,
    ): ArenaTicketRecordEntity {
        val h = ticket.snapshot.header
        require(
            h.ownerUid == request.ownerUid && h.requestId == request.requestId && h.difficulty == request.difficulty,
        )
        when (request) {
            is ArenaPrepareRequest.Random -> require(h.mode.name == "RANDOM")
            is ArenaPrepareRequest.Selected ->
                require(
                    h.mode.name == "SELECTED" && h.lessonId == request.lessonId &&
                        h.priceNolics <= request.maxPriceNolics,
                )
        }
        return record(ticket, now)
    }

    fun header(row: ArenaTicketRecordEntity): ArenaTicketHeader {
        require("status" !in json(row.termsJson))
        // Parser validates immutable identity/price/date; the temporary status is not persisted.
        val public = JsonObject(json(row.termsJson) + ("status" to JsonPrimitive("RESERVED")))
        val wire =
            buildJsonObject {
                put("tickets", JsonArray(listOf(public)))
                put("nextTicketId", JsonNull)
            }
        return parser.list(wire.toString(), ArenaTicketListRequest(row.ownerUid, pageSize = 1)).tickets.single().also {
            require(it.ticketId == row.ticketId && it.attemptId == row.attemptId && it.requestId == row.requestId)
        }.copy(status = ArenaTicketStatus.valueOf(row.serverStatus))
    }

    fun decode(row: ArenaTicketRecordEntity): ArenaEntryTicket {
        val snapshot = requireNotNull(row.snapshotJson) { "Header-only recovery is not launch permission" }
        require(sha256(snapshot) == row.snapshotSha256)
        header(row)
        val ticket =
            JsonObject(
                json(row.termsJson) + json(snapshot) +
                    mapOf(
                        "status" to JsonPrimitive(row.serverStatus),
                        "terminal" to (row.terminalJson?.let(Json::parseToJsonElement) ?: JsonNull),
                    ),
            )
        return parser.get(
            buildJsonObject {
                put("status", "ISSUED")
                put("ticket", ticket)
            }.toString(),
            ArenaTicketGetRequest(row.ownerUid, row.ticketId),
        )
    }

    fun terminal(
        row: ArenaTicketRecordEntity,
        result: ArenaCancelResponse,
        now: Long,
    ): ArenaTicketRecordEntity {
        val updated =
            when (result) {
                is ArenaCancelResponse.Cancelled -> {
                    require(result.ticketId == row.ticketId)
                    row.copy(serverStatus = "CANCELLED", terminalJson = cancellation(result.receipt).toString())
                }
                is ArenaCancelResponse.AlreadySettled -> {
                    require(result.ticketId == row.ticketId)
                    row.copy(serverStatus = "SETTLED", terminalJson = settlement(result.receipt).toString())
                }
                is ArenaCancelResponse.RetryRegeneration -> error("Retry is not a terminal observation")
            }
        // Works for header-only unsafe snapshots as well: cancellation remains available.
        val wire =
            buildJsonObject {
                put("status", updated.serverStatus)
                put("ticketId", row.ticketId)
                put("terminal", Json.parseToJsonElement(requireNotNull(updated.terminalJson)))
            }
        parser.cancel(
            wire.toString(),
            com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketCancelRequest(
                header(row),
                "validation",
            ),
        )
        return updated.copy(
            localUse = "UNUSED",
            decision = "NONE",
            observedAtMs = now,
            checkpointJson = null,
            checkpointRevision = 0,
        )
    }

    fun completion(
        row: ArenaTicketRecordEntity,
        intent: UnscoredTerminalIntent,
    ): ArenaCommandEntity {
        val ticket = decode(row).snapshot
        val id = intent.identity
        require(
            intent.completionKind == AttemptCompletionKind.COMPLETED,
        ) { "Arena abort requires explicit cancellation" }
        require(
            id.id.value == row.attemptId && id.userId == row.ownerUid && id.lessonId.value == ticket.header.lessonId,
        )
        require(id.lessonVersion == ticket.header.lessonVersion && id.mode == ticket.header.difficulty)
        val pairs = ticket.questions.map { it.questionId to it.order }.toSet()
        require(intent.served.all { (it.questionId.value to it.codeAnswerIndex) in pairs })
        require(intent.claims.raw.length == ticket.questions.size)
        val allowed = if (id.mode == Difficulty.HARD) setOf('.', 'P') else setOf('.', 'S')
        require(intent.claims.raw.all { it in allowed })
        val body =
            buildJsonObject {
                put("ownerUid", row.ownerUid)
                put("userId", row.ownerUid)
                put("ticketId", row.ticketId)
                put("attemptId", row.attemptId)
                put("lessonId", ticket.header.lessonId)
                put("lessonVersion", ticket.header.lessonVersion)
                put("difficulty", ticket.header.difficulty.name)
                put("scope", "public")
                put("sourceShelf", "arena")
                ticket.contentContext?.let { context ->
                    put("catalogId", context.catalogId)
                    put("questId", context.questId)
                    put("sectionId", context.sectionId)
                    put("themeId", context.themeId)
                }
                put("completionKind", "COMPLETED")
                put("completedAtMs", intent.endedAtMs)
                put("createdAtMs", intent.endedAtMs)
                put("chargeClaims", intent.claims.raw)
                put("answers", rawAnswers(intent))
                put("served", served(intent))
            }.toString()
        require(body.encodeToByteArray().size <= MAX_BODY_BYTES)
        return command(row.ownerUid, "SETTLE", row.attemptId, row.ticketId, row.attemptId, body, intent.endedAtMs)
    }

    /** Only verifies a retained first intent. Never reconstructs a missing bank or authorizes a new save. */
    fun completionReplay(
        row: ArenaTicketRecordEntity,
        retained: ArenaCommandEntity,
        intent: UnscoredTerminalIntent,
    ): ArenaCommandEntity {
        verify(retained)
        require(row.decision == "SETTLE" && retained.commandKind == "SETTLE")
        require(retained.ownerUid == row.ownerUid && retained.ticketId == row.ticketId)
        require(retained.attemptId == row.attemptId && retained.commandId == row.attemptId)
        val header = header(row)
        val identity = intent.identity
        require(intent.completionKind == AttemptCompletionKind.COMPLETED)
        require(identity.id.value == row.attemptId && identity.userId == row.ownerUid)
        require(identity.lessonId.value == header.lessonId && identity.lessonVersion == header.lessonVersion)
        require(identity.mode == header.difficulty)
        val frozen = json(retained.bodyJson)
        require(frozen.text("ownerUid") == row.ownerUid && frozen.text("userId") == row.ownerUid)
        require(frozen.text("ticketId") == row.ticketId && frozen.text("attemptId") == row.attemptId)
        require(frozen.text("lessonId") == header.lessonId && frozen.text("difficulty") == header.difficulty.name)
        require(frozen.getValue("lessonVersion") == JsonPrimitive(header.lessonVersion))
        require(frozen.text("scope") == "public" && frozen.text("sourceShelf") == "arena")
        require(frozen.text("completionKind") == "COMPLETED" && retained.createdAtMs == intent.endedAtMs)
        // Context comes only from the originally validated body. Every caller-supplied terminal field
        // is replaced and compared byte-for-byte, without a fake snapshot or a permissive codec.
        val supplied =
            buildJsonObject {
                put("completedAtMs", intent.endedAtMs)
                put("createdAtMs", intent.endedAtMs)
                put("chargeClaims", intent.claims.raw)
                put("answers", rawAnswers(intent))
                put("served", served(intent))
            }
        require(JsonObject(frozen + supplied).toString() == retained.bodyJson) { "First terminal intent changed" }
        return retained.copy(state = "PENDING", lastFailure = null)
    }

    private fun rawAnswers(intent: UnscoredTerminalIntent) =
        buildJsonArray {
            intent.answers.forEach { answer ->
                add(
                    buildJsonObject {
                        put("questionId", answer.questionId.value)
                        put("codeAnswerIndex", answer.codeAnswerIndex)
                        put("answerPayload", answerJson.encodeToString(UserAnswer.serializer(), answer.answer))
                        put("answeredAtMs", answer.answeredAtMs)
                        put("durationMs", answer.durationMs)
                        put("wasTimeout", answer.wasTimeout)
                    },
                )
            }
        }

    private fun served(intent: UnscoredTerminalIntent) =
        buildJsonArray {
            intent.served.forEach {
                add(
                    buildJsonObject {
                        put("questionId", it.questionId.value)
                        put("codeAnswerIndex", it.codeAnswerIndex)
                    },
                )
            }
        }

    fun outbox(cmd: ArenaCommandEntity): OutboxEntity {
        verify(cmd)
        require(cmd.commandKind == "SETTLE" && cmd.commandId == cmd.attemptId)
        return OutboxEntity(
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, cmd.commandId),
            ownerUid = cmd.ownerUid, operation = OutboxOperations.SUBMIT_ATTEMPT, payload = cmd.bodyJson,
            entityRef = "lesson_runner:attempt:${cmd.attemptId}", expectedVersion = null, state = "WAITING",
            attemptCount = 0, nextRetryAtMs = 0, lastError = null, createdAtMs = cmd.createdAtMs,
        )
    }

    private fun verify(cmd: ArenaCommandEntity) {
        require(cmd.bodyJson.encodeToByteArray().size <= MAX_BODY_BYTES && sha256(cmd.bodyJson) == cmd.bodySha256)
    }

    private fun command(
        owner: String,
        kind: String,
        id: String,
        ticket: String?,
        attempt: String?,
        body: String,
        now: Long,
    ) = ArenaCommandEntity(owner, kind, id, ticket, attempt, body, sha256(body), "PENDING", null, now)

    private fun terms(h: ArenaTicketHeader) =
        buildJsonObject {
            put("schemaVersion", h.schemaVersion)
            put("ticketId", h.ticketId)
            put("attemptId", h.attemptId)
            put("uid", h.ownerUid)
            put("requestId", h.requestId)
            put("mode", h.mode.name)
            put("difficulty", h.difficulty.name)
            put("lessonId", h.lessonId)
            put("lessonVersion", h.lessonVersion)
            put("priceNolics", h.priceNolics)
            put("lifeCost", h.lifeCost)
            put("issuedAtMs", h.issuedAtMs)
            put("dayId", h.dayId)
        }

    private fun snapshot(s: ArenaTicketSnapshot) =
        buildJsonObject {
            put("authorUid", s.authorUid)
            put("ratingVersion", s.ratingVersion)
            put(
                "contentContext",
                s.contentContext?.let { c ->
                    buildJsonObject {
                        put("catalogId", c.catalogId)
                        put("questId", c.questId)
                        put("sectionId", c.sectionId)
                        put("themeId", c.themeId)
                    }
                } ?: JsonNull,
            )
            put(
                "questions",
                buildJsonArray {
                    s.questions.forEach { q ->
                        add(
                            buildJsonObject {
                                put("questionId", q.questionId)
                                put("questionVersion", q.questionVersion)
                                put(
                                    "publicQuestion",
                                    buildJsonObject {
                                        put("id", q.questionId)
                                        put("version", q.questionVersion)
                                        put("lessonId", s.header.lessonId)
                                        put("difficulty", s.header.difficulty.name)
                                        put("order", q.order)
                                        put("language", q.language)
                                        put("payload", q.payload)
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }

    private fun cancellation(
        r: com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancellationReceipt,
    ) = buildJsonObject {
        put("receiptId", r.receiptId)
        put("cancelledAtMs", r.cancelledAtMs)
        put("releasedNolics", r.releasedNolics)
        put("releasedLifePoints", r.releasedLifePoints)
    }

    private fun settlement(r: com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaSettlementReceipt) =
        buildJsonObject {
            put("receiptId", r.receiptId)
            put("attemptId", r.attemptId)
            put("settledAtMs", r.settledAtMs)
            put("percentScore", r.percentScore)
            put("consumedNolics", r.consumedNolics)
            put("consumedLifePoints", r.consumedLifePoints)
            put("authorCreditNolics", r.authorCreditNolics)
            put("sinkNolics", r.sinkNolics)
            put("royaltyReserveDeltaNolics", r.royaltyReserveDeltaNolics)
        }

    private fun json(value: String): JsonObject = Json.parseToJsonElement(value).jsonObject

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.also { require(it.isString) }.content

    private companion object {
        const val MAX_BODY_BYTES = 500_000
    }
}
