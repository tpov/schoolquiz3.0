package com.tpov.schoolquiz.platform.firebase.network

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Конверт мутации: согласие с платными условиями едет в конверте, когда для операции есть, и не
 * приписывается, когда его нет (CAP-16, история 2.12).
 *
 * Транспорт SDK не поднимает — конверт вынесен в чистую функцию ровно затем, чтобы собираться и
 * проверяться в JVM-тесте, как таблица решений в [CallableErrorsTest].
 */
class MutationEnvelopeTest {

    private fun record(operation: String) =
        OutboxRecord(
            id = 1L,
            mutationId = "lesson-runner-SUBMIT-ATTEMPT-attempt-1",
            ownerUid = "uid-1",
            operation = operation,
            payload = """{"attemptId":"attempt-1"}""",
            state = OutboxState.WAITING,
            createdAtMs = 1_000L,
            entityRef = "lesson_runner:attempt:attempt-1",
            expectedVersion = null,
        )

    @Test
    fun `given consent for a terms operation then the envelope names it`() {
        val envelope = mutationEnvelope(record(OutboxOperations.SUBMIT_ATTEMPT), """{"version":3}""")

        val expectedTerms: Any? = """{"version":3}"""
        assertEquals(expectedTerms, envelope["capturedTerms"])
        val expectedMutationId: Any? = "lesson-runner-SUBMIT-ATTEMPT-attempt-1"
        assertEquals(expectedMutationId, envelope["mutationId"])
        assertEquals(OutboxOperations.SUBMIT_ATTEMPT, envelope["operation"])
    }

    @Test
    fun `given no consent then the field is absent, never faked`() {
        val envelope = mutationEnvelope(record(OutboxOperations.SUBMIT_ATTEMPT), null)

        assertNull("согласия нет — серверу отвечают отказом, а не пустой записью", envelope["capturedTerms"])
    }

    @Test
    fun `given an operation without relevant terms then no consent and no version ride`() {
        val envelope = mutationEnvelope(record(OutboxOperations.READY_ACK), null)

        assertNull(envelope["capturedTerms"])
        assertNull("неверсионируемая операция версии не носит", envelope["expectedVersion"])
    }

    @Test
    fun `given a versioned record then the expected version rides beside the consent`() {
        val versioned = record(OutboxOperations.SUBMIT_ATTEMPT).copy(expectedVersion = 11L)

        val envelope = mutationEnvelope(versioned, """{"version":3}""")

        val expectedVersion: Any? = envelope["expectedVersion"]
        assertEquals(11L, expectedVersion)
        val expectedTerms: Any? = """{"version":3}"""
        assertEquals(expectedTerms, envelope["capturedTerms"])
    }

    @Test
    fun `given the registry then every terms operation is a permanent receipt operation`() {
        // Платная операция — деньги игрока: её отказ по согласию не имеет права докатиться до
        // карантина по возрасту или порогу попыток. Контракт держится на том, что реестр согласия
        // — подмножество реестра вечных квитанций.
        assertTrue(
            "операции согласия вне вечных квитанций: " +
                (OutboxOperations.termsConsentOperations - OutboxOperations.permanentReceiptOperations),
            OutboxOperations.termsConsentOperations.all { it in OutboxOperations.permanentReceiptOperations },
        )
        assertFalse("реестр пуст: согласие никуда не едет", OutboxOperations.termsConsentOperations.isEmpty())
    }
}
