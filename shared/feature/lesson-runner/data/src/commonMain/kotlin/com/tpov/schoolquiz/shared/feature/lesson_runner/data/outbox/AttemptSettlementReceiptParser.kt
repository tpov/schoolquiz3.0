package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.core.scoring.computePercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptReward
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.identity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Queue ACKs are deliberately opaque. Only the actual result.receipts contract can confirm
 * scoring; an old successful callable with no receipts stays LEGACY_RECORDED, not SETTLED.
 */
class AttemptSettlementReceiptParser {
    fun parse(
        receipt: MutationReceipt,
        expected: Attempt,
    ): ServerAttemptSettlement = parse(receipt, expected.identity())

    fun parse(
        receipt: MutationReceipt,
        expected: AttemptIdentity,
    ): ServerAttemptSettlement =
        try {
            requireIdentity(receipt, expected)
            val body = matchingBody(receipt, expected)
            val header = readHeader(body, expected)
            if (header.state == ServerAttemptSettlementState.SETTLED) {
                readAccounting(body, header)
            } else {
                readUnsettled(body, header)
            }
        } catch (failure: InvalidReceipt) {
            unknown(failure.state, failure.wireStatus)
        }

    private fun requireIdentity(
        receipt: MutationReceipt,
        expected: AttemptIdentity,
    ) {
        if (receipt.ownerUid != expected.userId || receipt.operation != OutboxOperations.SUBMIT_ATTEMPT) invalid()
        if (receipt.entityRef != LessonResultEntityRef.attempt(expected.id.value)) invalid()
        if (receipt.mutationId != OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, expected.id.value)) {
            invalid()
        }
    }

    private fun matchingBody(
        receipt: MutationReceipt,
        expected: AttemptIdentity,
    ): JsonObject {
        val envelope =
            runCatching { Json.parseToJsonElement(receipt.payloadJson) as? JsonObject }.getOrNull()
                ?: invalid()
        val result = envelope["result"] as? JsonObject ?: legacy()
        if ("receipts" !in result) legacy()
        val receipts = result["receipts"] as? JsonArray ?: invalid()
        val matching =
            receipts.filterIsInstance<JsonObject>()
                .filter { it.text("attemptId") == expected.id.value && it.text("uid") == expected.userId }
                .distinct()
        if (matching.size != 1) invalid()
        return matching.single()
    }

    private fun readHeader(
        body: JsonObject,
        expected: AttemptIdentity,
    ): SettlementHeader {
        val status = body.text("status")
        if (body.whole("schemaVersion") != 1L ||
            body.text("lessonId") != expected.lessonId.value ||
            body.whole("lessonVersion") != expected.lessonVersion
        ) {
            invalid(status)
        }
        val receiptId = body.text("receiptId")?.takeIf { it.isNotBlank() } ?: invalid(status)
        val settledAtMs = body.requiredWhole("settledAtMs", status)
        val completed = (body["completed"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
        if (body["completed"] != null && body["completed"] != JsonNull && completed == null) invalid(status)
        return SettlementHeader(readState(status), status, receiptId, settledAtMs, completed)
    }

    private fun readState(status: String?): ServerAttemptSettlementState =
        when (status) {
            "SETTLED" -> ServerAttemptSettlementState.SETTLED
            "UNSCORABLE" -> ServerAttemptSettlementState.UNSCORABLE
            "REJECTED" -> ServerAttemptSettlementState.REJECTED
            "LEGACY_RECORDED" -> ServerAttemptSettlementState.LEGACY_RECORDED
            "PENDING" -> ServerAttemptSettlementState.PENDING
            else -> invalid(status)
        }

    private fun readUnsettled(
        body: JsonObject,
        header: SettlementHeader,
    ): ServerAttemptSettlement {
        if (header.completed == true) invalid(header.wireStatus)
        // Never display client-like score fields from an unsettled server reply.
        if (listOf("codeAnswer", "percentScore").any { body[it] != null && body[it] != JsonNull }) {
            invalid(header.wireStatus)
        }
        return ServerAttemptSettlement(
            header.state,
            header.wireStatus,
            header.receiptId,
            completed = header.completed,
            settledAtMs = header.settledAtMs,
            ownerVerified = true,
        )
    }

    private fun readAccounting(
        body: JsonObject,
        header: SettlementHeader,
    ): ServerAttemptSettlement {
        val status = header.wireStatus
        val rawCode = body.text("codeAnswer") ?: invalid(status)
        val rawPercent =
            (body["percentScore"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
                ?: invalid(status)
        val code = runCatching { CodeAnswer(rawCode) }.getOrNull() ?: invalid(status)
        val percent = runCatching { PercentScore(rawPercent) }.getOrNull() ?: invalid(status)
        if (computePercentScore(code) != percent) invalid(status)
        val life = body.requiredWhole("lifePointsSpent", status)
        val plasma = body.requiredWhole("plasmaPointsSpent", status)
        val reward = body["reward"] as? JsonObject ?: invalid(status)
        val skillPoints = reward.requiredWhole("skillPoints", status)
        val nolics = reward.requiredWhole("nolics", status)
        // Missing old completed does not become true. Accounting may still be genuine.
        return ServerAttemptSettlement(
            state = header.state,
            wireStatus = status,
            receiptId = header.receiptId,
            codeAnswer = code,
            percentScore = percent,
            completed = header.completed,
            settledAtMs = header.settledAtMs,
            lifePointsSpent = life,
            plasmaPointsSpent = plasma,
            reward = ServerAttemptReward(skillPoints, nolics),
            ownerVerified = true,
        )
    }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.whole(key: String): Long? =
        (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            ?.takeIf { it in 0..MAX_SAFE_SERVER_INTEGER }

    private fun JsonObject.requiredWhole(
        key: String,
        status: String?,
    ): Long = whole(key) ?: invalid(status)

    private fun invalid(status: String? = null): Nothing =
        throw InvalidReceipt(ServerAttemptSettlementState.INVALID_RECEIPT, status)

    private fun legacy(): Nothing = throw InvalidReceipt(ServerAttemptSettlementState.LEGACY_RECORDED)

    private fun unknown(
        state: ServerAttemptSettlementState,
        status: String? = null,
    ) = ServerAttemptSettlement(state = state, wireStatus = status)

    private class InvalidReceipt(
        val state: ServerAttemptSettlementState,
        val wireStatus: String? = null,
    ) : Exception()

    private data class SettlementHeader(
        val state: ServerAttemptSettlementState,
        val wireStatus: String?,
        val receiptId: String,
        val settledAtMs: Long,
        val completed: Boolean?,
    )

    private companion object {
        const val MAX_SAFE_SERVER_INTEGER = 9_007_199_254_740_991L
    }
}
