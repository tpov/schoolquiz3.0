@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ArenaOnlineO2Adapter
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ArenaRunTransitions
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.initialArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDelivery
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDeliveryVerdict
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowView
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Конформность онлайн-HARD переходов арены общему контракту ONLINE-O2 (история 3.2 эпика
 * `epic-arena-completion`; контракт `_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md`,
 * принят владельцем 2026-10-08). По одному доказательству на обязательство заголовка истории:
 * исходный deadline продолжается через разрыв, захваченная конфигурация окна переживает retry,
 * вторых часов нет, потерянный ACK не оставляет неограниченного gap.
 */
class ArenaOnlineO2AdapterTest {
    private val attemptId = "b".repeat(64)

    // ── §3: окно не начинается после последнего вопроса попытки ─────────────────────────────

    @Test
    fun noWindowBeginsAfterTheAttemptsLastQuestion() {
        val binding = fixture().binding
        assertNull(ArenaOnlineO2Adapter.windowRequestOrNull(binding, 1), "the last question opens no window")
        assertNull(ArenaOnlineO2Adapter.windowRequestOrNull(binding, 2))
        assertFailsWith<IllegalArgumentException> { ArenaOnlineO2Adapter.windowRequestOrNull(binding, -1) }
    }

    // ── §2/§7: событие завершения детерминировано — повтор читает то же окно, а не второе ────

    @Test
    fun windowRequestIsDeterministicFromTheFrozenSnapshotAndRetryBuildsTheSame() {
        val binding = fixture().binding
        val expected =
            QuestionWindowRequest(
                attemptId = attemptId,
                lessonId = "lesson",
                lessonVersion = 3,
                completedQuestionIndex = 0,
                nextQuestionId = "q2",
                isHard = true,
            )
        assertEquals(expected, ArenaOnlineO2Adapter.windowRequestOrNull(binding, 0))
        repeat(3) { assertEquals(expected, ArenaOnlineO2Adapter.windowRequestOrNull(binding, 0)) }
    }

    // ── §6/AD-6: чужое окно не читается — identity прежде фактов ─────────────────────────────

    @Test
    fun windowIdentityMismatchIsRejectedBeforeAnyFactIsRead() {
        val binding = fixture().binding
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.requireWindowIdentity(binding, 0, window(attempt = "other-attempt"))
        }
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.requireWindowIdentity(binding, 1, window())
        }
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.requireWindowIdentity(binding, 0, window(nextQuestionIndex = 5))
        }
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.requireWindowIdentity(binding, 0, window(id = " "))
        }
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.requireWindowIdentity(binding, 0, window(seconds = 0))
        }
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.requireWindowIdentity(binding, 0, window(allocated = -1))
        }
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.requireWindowIdentity(binding, 0, window(expires = OPENED_AT_MS))
        }
        // Валидное окно проходит и повторно: повтор begin возвращает то же окно (§7).
        assertEquals(window(), ArenaOnlineO2Adapter.requireWindowIdentity(fixture().binding, 0, window()))
    }

    // ── §3/§5: первая выдача — сервер ставит исходные часы, клиент строит легальный переход ──

    @Test
    fun firstDeliveryAdoptsTheServerDeadlineExactlyOnceThroughALegalPausedResume() {
        val paused = pausedAtNextQuestion()
        val candidate = ArenaOnlineO2Adapter.resumeDelivered(paused, 0, window(), delivered(), LOCAL_NOW_MS)

        assertEquals(ArenaRunnerPhase.ANSWERING, candidate.phase)
        assertEquals(1, candidate.indexInPool)
        assertEquals(LOCAL_NOW_MS + 24_000, candidate.deadlineMs, "the projection is anchored once, from the server remainder")
        assertEquals(paused.checkpoint.questionStartedAtMs, candidate.questionStartedAtMs, "the slot is the same position")
    }

    @Test
    fun deliveryOutsideTheAttemptSnapshotOrWindowIsRejected() {
        val paused = pausedAtNextQuestion()
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.resumeDelivered(paused, 0, window(), delivered(position = 2), LOCAL_NOW_MS)
        }
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.resumeDelivered(paused, 0, window(), delivered(question = "q9"), LOCAL_NOW_MS)
        }
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.resumeDelivered(paused, 0, window(), delivered(window = "win_other"), LOCAL_NOW_MS)
        }
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.resumeDelivered(paused, 0, window(), delivered(remainder = -1), LOCAL_NOW_MS)
        }
    }

    @Test
    fun resumeRefusesAnythingButTheBetweenQuestionsPauseAtTheDeliveredSlot() {
        val answering = fixture().continuable
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.resumeDelivered(answering, 0, window(), delivered(), LOCAL_NOW_MS)
        }
        val pausedAtUndeliveredSlot =
            answering.copy(checkpoint = answering.checkpoint.copy(phase = ArenaRunnerPhase.PAUSED, indexInPool = 0))
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.resumeDelivered(pausedAtUndeliveredSlot, 0, window(), delivered(), LOCAL_NOW_MS)
        }
    }

    // ── §7: потерянный ACK/reconnect — исходный deadline продолжается, вторых часов нет ──────

    @Test
    fun redeliveryReconfirmsTheRunningQuestionWithoutResettingOrExtendingItsDeadline() {
        val answering = resumedAfterDelivery()
        val stored: ArenaRunnerCheckpoint = answering.checkpoint
        // Сервер отвечает тем же вопросом; локальное «сейчас» уже сильно позже якоря выдачи.
        val again = delivered(remainder = 10_000)
        assertEquals(stored, ArenaOnlineO2Adapter.reconfirmDelivered(answering, 0, window(), again))

        // Даже испорченный/сместившийся серверный ответ не двигает часы: окно одно, выдача одна.
        val rogue = delivered(answerDeadline = ISSUED_AT_MS + 600_000, remainder = 500_000)
        assertEquals(stored, ArenaOnlineO2Adapter.reconfirmDelivered(answering, 0, window(), rogue))
    }

    // ── §5: серверный остаток честный — ноль не растягивается в добавленное время ─────────────

    @Test
    fun exhaustedRemainderIsAdoptedAsIsAndTheExistingTimeoutRuleClosesTheQuestion() {
        val paused = pausedAtNextQuestion()
        val candidate = ArenaOnlineO2Adapter.resumeDelivered(paused, 0, window(), delivered(remainder = 0), LOCAL_NOW_MS)
        assertEquals(LOCAL_NOW_MS, candidate.deadlineMs)
        // Существующее режимное правило добивает просроченный слот без ответа (AD-31).
        val closed = ArenaRunTransitions.timeout(paused.copy(checkpoint = candidate), LOCAL_NOW_MS)
        assertEquals(2, closed.indexInPool, "a two-question attempt walks to completion")
    }

    // ── §7: истёкшее окно — решение о закрытии, без ready-ACK, без часов, повтор стабилен ─────

    @Test
    fun expiredWindowYieldsAClosureDecisionAndNeverAReadyAckOrAClock() {
        val paused = pausedAtNextQuestion()
        val expired = QuestionDeliveryVerdict.WindowExpired("win_test", closedAtMs = 500_000, serverNowMs = 500_000)
        val decision = ArenaOnlineO2Adapter.requireExpiryConsistent(paused, 0, window(), expired)
        assertEquals(attemptId, decision.attemptId)
        assertEquals(0, decision.completedQuestionIndex)
        assertEquals("win_test", decision.windowId)
        // Повтор запроса выдачи остаётся стабильно истёкшим — неограниченного gap нет (§7).
        ArenaOnlineO2Adapter.requireExpiryConsistent(paused, 0, window(), expired.copy(closedAtMs = 500_100))
        // Выдачи не было — слот не мог начаться; сочетание «истекло + уже отвечает» противоречиво.
        assertFailsWith<IllegalArgumentException> {
            ArenaOnlineO2Adapter.requireExpiryConsistent(resumedAfterDelivery(), 0, window(), expired)
        }
    }

    // ── фикстуры ─────────────────────────────────────────────────────────────────────────────

    /** Двухвопросная HARD-попытка: закрыли q1 (таймаут фона) → пауза на слоте q2. */
    private fun fixture(): Fixture {
        val difficulty = Difficulty.HARD
        val questions = (1..2).map { index -> redactedSingleChoiceContent(difficulty = "HARD").copy(id = "q$index") }
        val header =
            ArenaTicketHeader(
                1, "a".repeat(64), attemptId, "owner", "request", ArenaEntryMode.SELECTED,
                difficulty, "lesson", 3, ArenaTicketStatus.RESERVED, 1, 50, 100, "2026-09-08",
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
        val binding = ArenaRunnerCheckpointBinding(snapshot, "c".repeat(64))
        val duration = computeTimer(questions.first(), difficulty, TimerCoefficients.Default, SessionMode.LEARNING).seconds * 1000L
        val checkpoint =
            initialArenaRunnerCheckpoint(binding, ArenaInitialCheckpoint(SessionMode.LEARNING, 91, 1000, 1000 + duration))
        val continuable = ArenaRunRead.Continuable(binding, checkpoint, 0)
        return Fixture(continuable, binding)
    }

    private data class Fixture(
        val continuable: ArenaRunRead.Continuable,
        val binding: ArenaRunnerCheckpointBinding,
    )

    private fun pausedAtNextQuestion(): ArenaRunRead.Continuable {
        val (continuable, _) = fixture()
        val paused = ArenaRunTransitions.background(continuable, 100_000)
        assertEquals(ArenaRunnerPhase.PAUSED, paused.phase)
        assertEquals(1, paused.indexInPool)
        return continuable.copy(checkpoint = paused)
    }

    private fun resumedAfterDelivery(): ArenaRunRead.Continuable {
        val paused = pausedAtNextQuestion()
        val resumed = ArenaOnlineO2Adapter.resumeDelivered(paused, 0, window(), delivered(), 120_000)
        return paused.copy(checkpoint = resumed)
    }

    private fun window(
        attempt: String = attemptId,
        id: String = "win_test",
        nextQuestionIndex: Int = 1,
        seconds: Int = 60,
        allocated: Int = 24,
        expires: Long = OPENED_AT_MS + 60_000,
    ): QuestionWindowView =
        QuestionWindowView(
            windowId = id,
            attemptId = attempt,
            completedQuestionIndex = 0,
            nextQuestionIndex = nextQuestionIndex,
            state = "OPEN",
            windowSeconds = seconds,
            allocatedSeconds = allocated,
            openedAtMs = OPENED_AT_MS,
            expiresAtMs = expires,
        )

    private fun delivered(
        position: Int = 1,
        question: String = "q2",
        answerDeadline: Long = ISSUED_AT_MS + 24_000,
        remainder: Long = 24_000,
        window: String = "win_test",
    ): QuestionDeliveryVerdict.Delivered =
        QuestionDeliveryVerdict.Delivered(
            windowId = window,
            delivery = QuestionDelivery(position, question, ISSUED_AT_MS, answerDeadline),
            remainderMs = remainder,
            serverNowMs = ISSUED_AT_MS,
        )

    private companion object {
        const val OPENED_AT_MS = 100_000L
        const val ISSUED_AT_MS = 150_000L
        const val LOCAL_NOW_MS = 200_000L
    }
}
