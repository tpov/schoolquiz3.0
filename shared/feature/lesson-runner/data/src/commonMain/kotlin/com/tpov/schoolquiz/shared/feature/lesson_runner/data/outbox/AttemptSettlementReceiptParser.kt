package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.core.scoring.computePercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.identity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptReward
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
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
    fun parse(receipt: MutationReceipt, expected: Attempt): ServerAttemptSettlement = parse(receipt, expected.identity())

    fun parse(receipt: MutationReceipt, expected: AttemptIdentity): ServerAttemptSettlement {
        if (receipt.ownerUid != expected.userId ||
            receipt.operation != OutboxOperations.SUBMIT_ATTEMPT ||
            receipt.entityRef != LessonResultEntityRef.attempt(expected.id.value) ||
            receipt.mutationId != OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, expected.id.value)
        ) return unknown(ServerAttemptSettlementState.INVALID_RECEIPT)

        val envelope = runCatching { Json.parseToJsonElement(receipt.payloadJson) as? JsonObject }.getOrNull()
            ?: return unknown(ServerAttemptSettlementState.INVALID_RECEIPT)
        val result = envelope["result"] as? JsonObject
            ?: return unknown(ServerAttemptSettlementState.LEGACY_RECORDED)
        if ("receipts" !in result) return unknown(ServerAttemptSettlementState.LEGACY_RECORDED)
        val receipts = result["receipts"] as? JsonArray
            ?: return unknown(ServerAttemptSettlementState.INVALID_RECEIPT)
        val matching = receipts.filterIsInstance<JsonObject>()
            .filter { it.text("attemptId") == expected.id.value && it.text("uid") == expected.userId }
            .distinct()
        if (matching.size != 1) return unknown(ServerAttemptSettlementState.INVALID_RECEIPT)
        return parseMatched(matching.single(), expected)
    }

    private fun parseMatched(body: JsonObject, expected: AttemptIdentity): ServerAttemptSettlement {
        val status = body.text("status")
        fun invalid() = unknown(ServerAttemptSettlementState.INVALID_RECEIPT, status)
        if (body.whole("schemaVersion") != 1L ||
            body.text("lessonId") != expected.lessonId.value ||
            body.whole("lessonVersion") != expected.lessonVersion
        ) return invalid()
        val receiptId = body.text("receiptId")?.takeIf { it.isNotBlank() } ?: return invalid()
        val settledAtMs = body.whole("settledAtMs") ?: return invalid()
        val completed = (body["completed"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
        if (body["completed"] != null && body["completed"] != JsonNull && completed == null) return invalid()
        val state = when (status) {
            "SETTLED" -> ServerAttemptSettlementState.SETTLED
            "UNSCORABLE" -> ServerAttemptSettlementState.UNSCORABLE
            "REJECTED" -> ServerAttemptSettlementState.REJECTED
            "LEGACY_RECORDED" -> ServerAttemptSettlementState.LEGACY_RECORDED
            "PENDING" -> ServerAttemptSettlementState.PENDING
            else -> return invalid()
        }
        if (state != ServerAttemptSettlementState.SETTLED) {
            if (completed == true) return invalid()
            // Never display client-like score fields from an unsettled server reply.
            if (body["codeAnswer"] != null && body["codeAnswer"] != JsonNull) return invalid()
            if (body["percentScore"] != null && body["percentScore"] != JsonNull) return invalid()
            return ServerAttemptSettlement(
                state, status, receiptId, completed = completed, settledAtMs = settledAtMs, ownerVerified = true,
            )
        }
        val rawCode = body.text("codeAnswer") ?: return invalid()
        val rawPercent = (body["percentScore"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            ?: return invalid()
        val code = runCatching { CodeAnswer(rawCode) }.getOrNull() ?: return invalid()
        val percent = runCatching { PercentScore(rawPercent) }.getOrNull() ?: return invalid()
        if (computePercentScore(code) != percent) return invalid()
        val life = body.whole("lifePointsSpent") ?: return invalid()
        val plasma = body.whole("plasmaPointsSpent") ?: return invalid()
        val reward = body["reward"] as? JsonObject ?: return invalid()
        val skillPoints = reward.whole("skillPoints") ?: return invalid()
        val nolics = reward.whole("nolics") ?: return invalid()
        // Missing old completed does not become true. Accounting may still be genuine.
        return ServerAttemptSettlement(
            state = state,
            wireStatus = status,
            receiptId = receiptId,
            codeAnswer = code,
            percentScore = percent,
            completed = completed,
            settledAtMs = settledAtMs,
            lifePointsSpent = life,
            plasmaPointsSpent = plasma,
            reward = ServerAttemptReward(skillPoints, nolics),
            ownerVerified = true,
        )
    }

    private fun JsonObject.text(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.whole(key: String): Long? =
        (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            ?.takeIf { it in 0..MAX_SAFE_SERVER_INTEGER }

    private fun unknown(state: ServerAttemptSettlementState, status: String? = null) =
        ServerAttemptSettlement(state = state, wireStatus = status)

    private companion object {
        const val MAX_SAFE_SERVER_INTEGER = 9_007_199_254_740_991L
    }
}
