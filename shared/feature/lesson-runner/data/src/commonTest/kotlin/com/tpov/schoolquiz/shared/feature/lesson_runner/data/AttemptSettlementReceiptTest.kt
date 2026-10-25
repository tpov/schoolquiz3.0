package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.SettlementReplayReceipt
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.AttemptSettlementReceiptParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultEntityRef
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.OutboxServerAttemptSettlementRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttemptSettlementReceiptTest {
    private val parser = AttemptSettlementReceiptParser()
    private val attempt = Attempt(
        id = AttemptId("attempt-1"), userId = "owner-1", lessonId = LessonId("lesson-1"),
        lessonVersion = 4L, mode = Difficulty.EASY, codeAnswer = CodeAnswer("1"),
        percentScore = PercentScore(0), completedAt = 50L,
    )

    private fun body(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("receiptId", "attempt:server-hash")
        put("attemptId", attempt.id.value)
        put("uid", attempt.userId)
        put("lessonId", attempt.lessonId.value)
        put("lessonVersion", attempt.lessonVersion)
        put("status", "SETTLED")
        put("completed", true)
        put("settledAtMs", 1234L)
        put("codeAnswer", "9")
        put("percentScore", 100)
        put("lifePointsSpent", 50)
        put("plasmaPointsSpent", 100)
        put("reward", buildJsonObject { put("skillPoints", 7); put("nolics", 2) })
    }

    private fun wire(vararg bodies: JsonObject): MutationReceipt = MutationReceipt(
        ownerUid = attempt.userId,
        mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, attempt.id.value),
        operation = OutboxOperations.SUBMIT_ATTEMPT,
        entityRef = LessonResultEntityRef.attempt(attempt.id.value),
        payloadJson = buildJsonObject {
            put("replayed", false)
            put("result", buildJsonObject { put("receipts", JsonArray(bodies.toList())) })
        }.toString(),
        receivedAtMs = 9999L,
    )

    private fun changed(key: String, value: JsonElement) = JsonObject(body() + (key to value))

    @Test
    fun exactServerProofUsesServerScoreAndActualAmountsNotLocalFormula() {
        val value = parser.parse(wire(body()), attempt)
        assertTrue(value.isConfirmedCompletion)
        assertTrue(value.canQueueRating)
        assertEquals(100, value.percentScore?.raw)
        assertEquals("9", value.codeAnswer?.raw)
        assertEquals(7L, value.reward?.skillPoints)
        assertEquals(2L, value.reward?.nolics)
        assertEquals(50L, value.lifePointsSpent)
        assertEquals(100L, value.plasmaPointsSpent)
    }

    @Test
    fun unsetOrFalseCompletedKeepsAccountingButCannotProveCompletionOrRating() {
        listOf(JsonObject(body() - "completed"), changed("completed", JsonPrimitive(false))).forEach { body ->
            val value = parser.parse(wire(body), attempt)
            assertTrue(value.hasAccountingProof)
            assertFalse(value.isConfirmedCompletion)
            assertFalse(value.canQueueRating)
        }
        assertInvalid(wire(changed("completed", JsonPrimitive("true"))))
        assertInvalid(wire(changed("completed", JsonPrimitive(1))))
    }

    @Test
    fun savedUnknownRefusalAndLegacyNeverBecomeZeroScore() {
        listOf("UNSCORABLE", "REJECTED", "LEGACY_RECORDED", "PENDING").forEach { status ->
            val value = parser.parse(wire(JsonObject(body() + mapOf(
                "status" to JsonPrimitive(status), "completed" to JsonPrimitive(false),
                "codeAnswer" to JsonNull, "percentScore" to JsonNull,
            ))), attempt)
            assertEquals(ServerAttemptSettlementState.valueOf(status), value.state)
            assertNull(value.percentScore)
            assertNull(value.codeAnswer)
            assertNull(value.reward)
            assertNull(value.lifePointsSpent)
            assertFalse(value.isConfirmedCompletion)
        }
        val legacy = parser.parse(wire().copy(payloadJson = "{\"replayed\":false,\"result\":{\"ok\":true}}"), attempt)
        assertEquals(ServerAttemptSettlementState.LEGACY_RECORDED, legacy.state)
        assertNull(legacy.percentScore)
        assertFalse(legacy.canQueueRating)
    }

    @Test
    fun allIdentityAndVersionFieldsMustMatch() {
        val valid = wire(body())
        listOf(
            valid.copy(ownerUid = "other"), valid.copy(operation = "OTHER"),
            valid.copy(mutationId = "other"), valid.copy(entityRef = null), valid.copy(entityRef = "lesson-1"),
            wire(changed("uid", JsonPrimitive("other"))), wire(changed("attemptId", JsonPrimitive("other"))),
            wire(changed("lessonId", JsonPrimitive("other"))), wire(changed("lessonVersion", JsonPrimitive(5))),
            wire(changed("schemaVersion", JsonPrimitive(2))),
        ).forEach(::assertInvalid)
    }

    @Test
    fun unsafeNegativeQuotedMissingAndInconsistentNumbersAreRefused() {
        listOf("lifePointsSpent", "plasmaPointsSpent", "settledAtMs").forEach { key ->
            listOf(JsonPrimitive(-1), JsonPrimitive(9_007_199_254_740_992L), JsonPrimitive("50"), JsonNull)
                .forEach { assertInvalid(wire(changed(key, it))) }
        }
        listOf(-1L, 9_007_199_254_740_992L).forEach { bad ->
            assertInvalid(wire(changed("reward", buildJsonObject { put("skillPoints", bad); put("nolics", 2) })))
            assertInvalid(wire(changed("reward", buildJsonObject { put("skillPoints", 7); put("nolics", bad) })))
        }
        assertInvalid(wire(changed("percentScore", JsonPrimitive(0))))
        assertInvalid(wire(changed("percentScore", JsonPrimitive("100"))))
        assertInvalid(wire(changed("codeAnswer", JsonNull)))
        assertInvalid(wire(JsonObject(body() - "reward")))
        assertInvalid(wire(changed("status", JsonPrimitive("UNSCORABLE"))))
    }

    @Test
    fun genuineServerZeroIsDifferentFromUnknown() {
        val value = parser.parse(wire(JsonObject(body() + mapOf(
            "codeAnswer" to JsonPrimitive("1"), "percentScore" to JsonPrimitive(0),
        ))), attempt)
        assertTrue(value.isConfirmedCompletion)
        assertEquals(0, value.percentScore?.raw)
    }

    @Test
    fun settleReplayMarkerParsesDeliberatelyToLegacyRecordedNeverZero() {
        // Движок ACK-нул реплей settle маркером SettlementReplayReceipt после серверного
        // SETTLEMENT_REUSE: попытка уже зафиксирована, повтор терминален. Парсер обязан прочесть
        // маркер нарочно — LEGACY_RECORDED (засчитано без доказательства оценки), а не через
        // случайный провал в legacy-ветку и не как ноль или невалидную квитанцию.
        val marker = wire(body()).copy(payloadJson = SettlementReplayReceipt.PAYLOAD_JSON)
        val value = parser.parse(marker, attempt)
        assertEquals(ServerAttemptSettlementState.LEGACY_RECORDED, value.state)
        assertNull(value.percentScore, "никогда ноль: доказательства оценки нет вовсе")
        assertNull(value.codeAnswer)
        assertNull(value.reward)
        assertFalse(value.isConfirmedCompletion)
        assertFalse(value.canQueueRating, "повтору и оценке не подлежит")
    }

    @Test
    fun duplicateEquivalentReceiptIsIdempotentButContradictionIsRejected() {
        assertTrue(parser.parse(wire(body(), body()), attempt).isConfirmedCompletion)
        assertInvalid(wire(body(), changed("completed", JsonPrimitive(false))))
        assertInvalid(wire())
        assertInvalid(wire(body()).copy(payloadJson = "broken"))
    }

    @Test
    fun pendingThenReceiptThenAccountSwitchNeverLeaksOtherOwnerResult() = runTest {
        val account = MutableStateFlow<String?>(attempt.userId)
        val store = TestReceiptStore()
        val repo = OutboxServerAttemptSettlementRepository(store, { account })
        val observed = mutableListOf<ServerAttemptSettlement>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.observe(attempt).collect { observed += it } }
        assertEquals(ServerAttemptSettlementState.PENDING, observed.last().state)
        assertNull(observed.last().percentScore)
        store.persist(wire(body()))
        runCurrent()
        assertTrue(observed.last().isConfirmedCompletion)
        account.value = "owner-2"
        runCurrent()
        assertEquals(ServerAttemptSettlementState.ACCOUNT_UNAVAILABLE, observed.last().state)
        assertNull(observed.last().reward)
        account.value = null
        runCurrent()
        assertEquals(ServerAttemptSettlementState.ACCOUNT_UNAVAILABLE, observed.last().state)
        account.value = attempt.userId
        runCurrent()
        assertTrue(observed.last().isConfirmedCompletion)
        assertTrue(store.observedOwners.all { it == attempt.userId })
    }

    private fun assertInvalid(receipt: MutationReceipt) {
        val value = parser.parse(receipt, attempt)
        assertEquals(ServerAttemptSettlementState.INVALID_RECEIPT, value.state)
        assertNull(value.percentScore)
        assertNull(value.reward)
        assertFalse(value.canQueueRating)
    }
}

private class TestReceiptStore : MutationReceiptStore {
    private val rows = MutableStateFlow<List<MutationReceipt>>(emptyList())
    val observedOwners = mutableListOf<String>()

    override suspend fun persist(receipt: MutationReceipt) { rows.value = rows.value + receipt }
    override suspend fun find(ownerUid: String, mutationId: String): MutationReceipt? =
        rows.value.find { it.ownerUid == ownerUid && it.mutationId == mutationId }

    override fun observeEntity(ownerUid: String, entityRef: String): Flow<List<MutationReceipt>> {
        observedOwners += ownerUid
        return rows.map { list -> list.filter { it.ownerUid == ownerUid && it.entityRef == entityRef } }
    }
}
