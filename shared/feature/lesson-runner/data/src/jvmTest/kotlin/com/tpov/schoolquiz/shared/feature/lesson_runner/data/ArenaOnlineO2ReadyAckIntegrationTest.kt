@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaRunnerCheckpointCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.ReadyAckOutboxCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.ReadyAckReceiptParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.QuestionWindowWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ArenaOnlineO2Adapter
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ArenaRunTransitions
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.initialArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Один онлайн-HARD переход арены целиком, клиентская половина (история 3.2; контракт ONLINE-O2):
 * событие завершения → провод окна (формы истории 2.6) → выдача с серверным deadline → чекпоинт с
 * принятой проекцией → строка ready-ACK из тройки с окном сервера → серверная квитанция, чей
 * deadline — тот же серверный факт, что у выдачи. Половины 2.6 (кодек/разбор квитанции) и 3.2
 * (адаптер/парсер окна) сходятся в одном факте, а не в двух часах.
 */
class ArenaOnlineO2ReadyAckIntegrationTest {
    private val attemptId = "b".repeat(64)
    private val ownerUid = "owner"
    private val windowId = "win_1a2b3c4d5e6f"
    private val issuedAtMs = 1_760_000_010_000
    private val answerDeadlineMs = 1_760_000_034_000

    @Test
    fun oneTransitionCarriesTheServerDeadlineFromWireThroughCheckpointIntoTheReadyAckReceipt() {
        val binding = binding()
        val paused = pausedAtNextQuestion(binding)
        val identity =
            AttemptIdentity(
                AttemptId(attemptId),
                ownerUid,
                LessonId("lesson"),
                lessonVersion = 3,
                mode = Difficulty.HARD,
            )

        // Событие завершения: запрос из замороженного снапшота, окно — серверная форма 2.6.
        val request = ArenaOnlineO2Adapter.windowRequestOrNull(binding, 0)
        assertIs<com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowRequest>(request)
        val view = QuestionWindowWireParser().parseWindowView(windowWire())

        // Выдача: сервер ставит исходные часы; адаптер строит легальный PAUSED→ANSWERING переход.
        val verdict = QuestionWindowWireParser().parseDeliveryVerdict(deliveredWire())
        val delivered = assertIs<com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDeliveryVerdict.Delivered>(verdict)
        val resumed = ArenaOnlineO2Adapter.resumeDelivered(paused, 0, view, delivered, localNowMs = 1_760_000_012_500)
        assertEquals(ArenaRunnerPhase.ANSWERING, resumed.phase)
        assertEquals(1_760_000_034_000, resumed.deadlineMs, "anchor + server remainder reconstructs the same server deadline")

        // Ready-ACK: ключ рождается из тройки с окном сервера и не меняется при повторе.
        val row = ReadyAckOutboxCodec.buildReadyAckRow(identity, 1, view.windowId, createdAtMs = 1_760_000_012_500)
        assertEquals(OutboxOperations.READY_ACK, row.operation)
        assertEquals(OutboxState.WAITING.name, row.state)
        assertEquals(ReadyAckOutboxCodec.mutationKey(attemptId, 1, windowId), row.mutationId)
        assertEquals(row.mutationId, ReadyAckOutboxCodec.buildReadyAckRow(identity, 1, windowId, 999).mutationId)

        // Квитанция сервера: тот же deadline, что у выдачи, — один факт, а не вторые часы.
        val receipt =
            MutationReceipt(
                ownerUid = ownerUid,
                mutationId = row.mutationId,
                operation = OutboxOperations.READY_ACK,
                entityRef = ReadyAckOutboxCodec.entityRef(attemptId),
                payloadJson = receiptWire(replayed = false),
                receivedAtMs = 1_760_000_013_000,
            )
        val parsed = ReadyAckReceiptParser().parse(receipt, identity, 1, view.windowId)
        val acked = assertIs<com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.ReadyAckOutcome.Acked>(parsed)
        assertEquals(issuedAtMs, acked.receipt.issuedAtMs)
        assertEquals(answerDeadlineMs, acked.receipt.answerDeadlineMs)
        assertTrue(!acked.receipt.replayed)

        // Повтор ключа — та же квитанция: повтор провода даёт те же разобранные значения.
        val replay =
            receipt.copy(payloadJson = receiptWire(replayed = true), receivedAtMs = 1_760_000_050_000)
        val reParsed = ReadyAckReceiptParser().parse(replay, identity, 1, view.windowId)
        val reAcked = assertIs<com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.ReadyAckOutcome.Acked>(reParsed)
        assertEquals(acked.receipt.answerDeadlineMs, reAcked.receipt.answerDeadlineMs)
        assertTrue(reAcked.receipt.replayed)

        // Чекпоинт кодируется в журнал как обычно: принятый deadline живёт в тех же байтах.
        val codec = ArenaRunnerCheckpointCodec()
        val encoded = codec.encode(resumed, binding)
        val decoded = codec.decode(encoded, binding)
        assertEquals(resumed, decoded)
    }

    // ── фикстуры и серверные формы ───────────────────────────────────────────────────────────

    private fun binding(): ArenaRunnerCheckpointBinding {
        val questions =
            (1..2).map { index ->
                RedactedQuestionContent.SingleChoice(
                    id = "q$index",
                    difficulty = "HARD",
                    text = "Question $index",
                    imageUrl = null,
                    options =
                        listOf("A", "B", "C").map {
                            RedactedQuestionContent.Row("ri-$it", "Option $it")
                        },
                )
            }
        val header =
            ArenaTicketHeader(
                1, "a".repeat(64), attemptId, ownerUid, "request", ArenaEntryMode.SELECTED,
                Difficulty.HARD, "lesson", 3, ArenaTicketStatus.RESERVED, 1, 50, 100, "2026-09-08",
            )
        val snapshot =
            ArenaTicketSnapshot(
                header,
                "author",
                2,
                null,
                questions.mapIndexed { index, display ->
                    ArenaTicketQuestion("q${index + 1}", 2, index, "en", "not-parsed-by-domain", display)
                },
            )
        return ArenaRunnerCheckpointBinding(snapshot, "c".repeat(64))
    }

    private fun pausedAtNextQuestion(binding: ArenaRunnerCheckpointBinding): ArenaRunRead.Continuable {
        val duration =
            com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic
                .computeTimer(
                    binding.snapshot.questions.first().display,
                    Difficulty.HARD,
                    TimerCoefficients.Default,
                    SessionMode.LEARNING,
                ).seconds * 1000L
        val checkpoint =
            initialArenaRunnerCheckpoint(binding, ArenaInitialCheckpoint(SessionMode.LEARNING, 91, 1000, 1000 + duration))
        val continuable = ArenaRunRead.Continuable(binding, checkpoint, 0)
        val paused = ArenaRunTransitions.background(continuable, 100_000)
        assertEquals(ArenaRunnerPhase.PAUSED, paused.phase)
        return continuable.copy(checkpoint = paused)
    }

    /** Байт-в-байт форма `windowView` из `functions/index.js` (история 2.6). */
    private fun windowWire(): String =
        """
        {
          "schemaVersion": 1,
          "windowId": "$windowId",
          "attemptId": "$attemptId",
          "completedQuestionIndex": 0,
          "nextQuestionIndex": 1,
          "state": "OPEN",
          "windowSeconds": 60,
          "allocatedSeconds": 24,
          "openedAtMs": 1760000000000,
          "expiresAtMs": 1760000060000
        }
        """.trimIndent()

    /** Байт-в-байт форма вердикта `fetchNextLessonQuestion` — DELIVERED с серверными фактами. */
    private fun deliveredWire(): String =
        """
        {
          "status": "DELIVERED",
          "windowId": "$windowId",
          "questionIndex": 1,
          "questionId": "q2",
          "issuedAtMs": $issuedAtMs,
          "answerDeadlineMs": $answerDeadlineMs,
          "remainderMs": 21500,
          "serverNowMs": 1760000012500
        }
        """.trimIndent()

    /** Конверт `submitMutation` с квитанцией `applyLessonReadyAck` (реестр `MUTATION_HANDLERS`). */
    private fun receiptWire(replayed: Boolean): String =
        """
        {
          "result": {
            "schemaVersion": 1,
            "status": "ACKED",
            "attemptId": "$attemptId",
            "questionIndex": 1,
            "windowId": "$windowId",
            "issuedAtMs": $issuedAtMs,
            "answerDeadlineMs": $answerDeadlineMs,
            "ackedAtMs": 1760000130000
          },
          "replayed": $replayed
        }
        """.trimIndent()
}
