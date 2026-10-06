package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Клиентский инвариант контракта ONLINE-O2 §6 (identity/retry) на стороне владельца identity,
 * история 2.5 эпика `epic-ordinary-delivery` (полная редакция:
 * `_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md`).
 *
 * Всякое клиентское сообщение перехода несёт identity попытки и не меняется при retry (§6.1,
 * AD-6). Identity рождается один раз на старте ([RunnerState.Ready.attemptId], «Created once at
 * START») — значит, ни один переход рановера не чеканит её заново: принятый ответ, raw-путь,
 * клиентская проекция expiry и пауза/возобновление обязаны проносить одну и ту же identity.
 * Будущий онлайн-адаптер (история 2.6), который «заведёт новый attemptId на каждый вопрос»,
 * сломает этот контракт здесь, в модуле-владельце типа.
 */
class OnlineO2AttemptIdentityContractTest {
    private val state =
        makeReadyState(
            userId = "player-1",
            lessonId = "lesson-1",
            lessonVersion = 5L,
            mode = Difficulty.EASY,
            playOrder =
                listOf(
                    makeRunnerQuestion(id = "q1"),
                    makeRunnerQuestion(id = "q2", order = 1, codeAnswerIndex = 1),
                ),
        )

    private fun identityOf(s: RunnerState.Ready) =
        AttemptIdentity(s.attemptId, s.userId, s.lessonId, s.lessonVersion, s.mode)

    private fun assertIdentityCarried(expected: RunnerState.Ready, actual: RunnerState.Ready) {
        assertEquals(expected.attemptId, actual.attemptId)
        assertEquals(identityOf(expected), identityOf(actual))
    }

    @Test
    fun `accepted answer advance keeps the attempt identity`() {
        val answered = submitAnswer(state, UserAnswer.SingleChoiceAnswer(optId("A")), nowMs = 5_000L)
        assertEquals(state.indexInPool + 1, answered.indexInPool)
        assertIdentityCarried(state, answered)
    }

    @Test
    fun `raw answer advance keeps the attempt identity`() {
        val raw = state.copy(codeAnswer = null)
        val advanced = submitAnswer(raw, UserAnswer.SingleChoiceAnswer(optId("B")), nowMs = 6_000L)
        assertEquals(1, advanced.rawAnswers.size)
        assertIdentityCarried(raw, advanced)
    }

    @Test
    fun `timeout projection keeps the attempt identity`() {
        val answered = autoAnswerOnTimeout(state, randomSeed = 42L, nowMs = 7_000L)
        assertTrue(answered.answers.last().wasTimeout, "the expiry projection must have run")
        assertIdentityCarried(state, answered)
    }

    @Test
    fun `pause and resume keep the full identity`() {
        val resumed = state.copy(isPaused = true).copy(isPaused = false)
        assertIdentityCarried(state, resumed)
    }

    @Test
    fun `identity refuses an anonymous or unsized attempt`() {
        val identity = identityOf(state)
        assertFailsWith<IllegalArgumentException> { identity.copy(userId = " ") }
        assertFailsWith<IllegalArgumentException> { identity.copy(lessonVersion = -1L) }
    }
}
