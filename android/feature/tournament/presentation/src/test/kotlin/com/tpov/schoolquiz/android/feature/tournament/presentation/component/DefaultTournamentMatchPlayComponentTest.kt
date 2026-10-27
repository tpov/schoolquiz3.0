package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeOutboxStore
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeReceiptStore
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeTournamentRepository
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.operationView
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionIntent
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionOutboxCodec
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionOutboxRepository
import com.tpov.schoolquiz.shared.feature.tournament.data.outbox.TournamentSessionReceiptParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentEventWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentMeetingWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentSessionWireParser
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperationView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestion
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestionPayload
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestionRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionResult
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentWindowView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Игра матча (история 5.11): та же машина сессии, что у квалификации (история 5.10), на виде
 * MATCH — ответ только из очереди 5.9, квитанция приёмника ведёт готовность, закрытие матча
 * не применяет ответы, летевшие по нему.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentMatchPlayComponentTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var lifecycle: LifecycleRegistry
    private val session = OutboxSession("owner-1", 1)

    private val eventId = "event_champ_0000000001"
    private val matchId = "match_qf_00000001"
    private val matchSessionId = "m_" + "ab".repeat(32)
    private val questionId = "q000"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        lifecycle = LifecycleRegistry().apply { resume() }
    }

    @After
    fun tearDown() {
        lifecycle.destroy()
        Dispatchers.resetMain()
    }

    private fun sessionView(
        questionIndex: Int,
        status: String = "STARTED",
    ): TournamentSessionView =
        TournamentSessionView(
            sessionId = matchSessionId,
            eventId = eventId,
            kind = "MATCH",
            roundNumber = null,
            matchId = matchId,
            status = status,
            questionIndex = questionIndex,
            startedAtMs = 0L,
            endsAtMs = 100L,
            questionStartedAtMs = 0L,
            questionEndsAtMs = 50L,
            window = null,
        )

    private fun question(questionIndex: Int): TournamentPublicQuestion =
        TournamentPublicQuestion(
            questionId = if (questionIndex == 0) questionId else "q_next",
            type = "SingleChoice",
            difficulty = "EASY",
            contentVersion = 1,
            maxDurationMs = 21_000L,
            publicPayload =
                TournamentPublicQuestionPayload(
                    type = "SingleChoiceRedacted",
                    id = if (questionIndex == 0) questionId else "q_next",
                    difficulty = "EASY",
                    text = "A?",
                    imageUrl = null,
                    blanks = null,
                    protectedTextSegments = null,
                    options = listOf(TournamentPublicQuestionRow("a", "A"), TournamentPublicQuestionRow("b", "B")),
                    items = null,
                    candidates = null,
                ),
        )

    private fun readAnswer(questionIndex: Int): TournamentOperationView =
        operationView(session = sessionView(questionIndex), currentQuestion = question(questionIndex), serverNowMs = 10L)

    private inner class Fixture {
        val repository = FakeTournamentRepository()
        val outboxStore = FakeOutboxStore()
        val receiptStore = FakeReceiptStore()

        /** Сменяемая сессия: проверка наблюдателя под сменой аккаунта. */
        var currentSession: OutboxSession = session
        val sessions = OutboxSessionProvider { currentSession }
        val outbox =
            TournamentSessionOutboxRepository(
                outbox = outboxStore,
                receipts = receiptStore,
                sessions = sessions,
                parser = TournamentSessionReceiptParser(TournamentSessionWireParser(TournamentEventWireParser(), TournamentMeetingWireParser())),
                clock = { 1L },
            )
        val component =
            DefaultTournamentMatchPlayComponent(
                context = DefaultComponentContext(lifecycle),
                repository = repository,
                outbox = outbox,
                sessions = sessions,
                dispatcher = dispatcher,
                nowMs = { 1L },
            )

        /**
         * Квитанция приёмника для намерения: конверт {replayed, result} с тем же operationId.
         * P2: провод = серверная форма `responseFor` — окно операции вложено в `session`.
         */
        fun deliver(
            intent: TournamentSessionIntent,
            status: String,
            window: TournamentWindowView? = null,
            questionIndex: Int = 0,
        ) {
            val result =
                buildJsonObject {
                    put("schemaVersion", 1)
                    put("operationId", TournamentSessionOutboxCodec.operationId(intent))
                    put("status", status)
                    put("serverNowMs", 1)
                    put(
                        "session",
                        buildJsonObject {
                            put("sessionId", matchSessionId)
                            put("eventId", eventId)
                            put("kind", "MATCH")
                            put("matchId", matchId)
                            put("status", "STARTED")
                            put("questionIndex", questionIndex)
                            put("startedAtMs", 0)
                            put("endsAtMs", 100)
                            put("questionStartedAtMs", 0)
                            put("questionEndsAtMs", 50)
                            if (window != null) {
                                put(
                                    "window",
                                    buildJsonObject {
                                        put("windowId", window.windowId)
                                        put("completedQuestionIndex", window.completedQuestionIndex)
                                        put("nextQuestionIndex", window.nextQuestionIndex)
                                        put("windowSeconds", window.windowSeconds)
                                        put("openedAtMs", window.openedAtMs)
                                        put("expiresAtMs", window.expiresAtMs)
                                        put("state", window.state)
                                    },
                                )
                            }
                        },
                    )
                    if (intent is TournamentSessionIntent.ReadyAck) {
                        put(
                            "readyAck",
                            buildJsonObject {
                                put("schemaVersion", 1)
                                put("questionIndex", intent.questionIndex)
                                put("windowId", intent.windowId)
                                put("questionId", questionId)
                                put("issuedAtMs", 1)
                                put("answerDeadlineMs", 2)
                                put("ackedAtMs", 3)
                            },
                        )
                    }
                }
            val envelope =
                buildJsonObject {
                    put("replayed", false)
                    put("result", result)
                }
            receiptStore.receiptsByKey[TournamentSessionOutboxCodec.mutationKey(intent)] =
                MutationReceipt(
                    ownerUid = session.ownerUid,
                    mutationId = TournamentSessionOutboxCodec.mutationKey(intent),
                    operation = TournamentSessionOutboxCodec.operation,
                    entityRef = TournamentSessionOutboxCodec.entityRef(matchSessionId),
                    payloadJson = envelope.toString(),
                    receivedAtMs = 1L,
                )
        }
    }

    private suspend fun TestScope.openSession(f: Fixture) {
        f.repository.readSessionResult = Result.success(readAnswer(0))
        f.component.onOpenMatchSession(eventId, matchId, matchSessionId)
        advanceUntilIdle()
    }

    @Test
    fun `opening the match session plays the question and answers through the outbox`() = runTest(dispatcher) {
        val f = Fixture()
        openSession(f)
        val play = requireNotNull(f.component.state.value.play)
        assertEquals(matchSessionId, play.sessionId)
        assertEquals(matchId, f.component.state.value.matchId)
        assertEquals("A?", play.question?.publicPayload?.text)

        // Та же машина: ответ уезжает из очереди, квитанция несёт доставленное (DELIVERED) окно,
        // вложенное в session — серверная форма `responseFor` (P2/L1).
        val answerIntent =
            TournamentSessionIntent.Answer(
                sessionId = matchSessionId,
                questionIndex = 0,
                questionId = questionId,
                answerPayload = buildJsonObject { put("type", "single-choice"); put("selected", "a") },
            )
        val ackIntent =
            TournamentSessionIntent.ReadyAck(
                sessionId = matchSessionId,
                questionIndex = 1,
                windowId = "win_" + "cd".repeat(16),
            )
        val deliveredWindow =
            TournamentWindowView(
                windowId = ackIntent.windowId,
                completedQuestionIndex = 0,
                nextQuestionIndex = 1,
                windowSeconds = 5,
                openedAtMs = 1L,
                expiresAtMs = 101L,
                state = "DELIVERED",
            )
        f.deliver(answerIntent, status = "ANSWER_ACCEPTED", window = deliveredWindow, questionIndex = 1)
        f.deliver(ackIntent, status = "READY_ACK", questionIndex = 1)
        f.repository.readSessionResult = Result.success(readAnswer(1))

        f.component.onToggleChoice("a")
        f.component.onSubmitAnswerClick()
        advanceUntilIdle()

        assertEquals(2, f.outboxStore.rows.size)
        f.outboxStore.markDelivered()
        advanceUntilIdle()
        val playAfter = requireNotNull(f.component.state.value.play)
        assertEquals(1, playAfter.questionIndex)
        assertEquals(ackIntent.windowId, playAfter.readyAckWindowId)
        assertFalse(playAfter.awaitingDelivery)
    }

    @Test
    fun `a settled match session shows the server result without invented time`() = runTest(dispatcher) {
        val f = Fixture()
        openSession(f)
        val answerIntent =
            TournamentSessionIntent.Answer(
                sessionId = matchSessionId,
                questionIndex = 0,
                questionId = questionId,
                answerPayload = buildJsonObject { put("type", "single-choice"); put("selected", "a") },
            )
        f.deliver(answerIntent, status = "COMPLETED", questionIndex = 0)
        f.repository.readSessionResult =
            Result.success(
                operationView(
                    session = sessionView(0, status = "SETTLED"),
                    result = TournamentSessionResult(percentScore = 90.0, elapsedMs = 1_000L, completedAtMs = 1_000L),
                ),
            )

        f.component.onToggleChoice("a")
        f.component.onSubmitAnswerClick()
        advanceUntilIdle()

        val play = requireNotNull(f.component.state.value.play)
        assertTrue(play.settled)
        assertEquals(90.0, play.result?.percentScore)
    }

    @Test
    fun `closing the match leaves answers in flight unapplied`() = runTest(dispatcher) {
        val f = Fixture()
        openSession(f)
        // Ответ уехал, квитанции ещё нет: строка честно ждёт в очереди.
        f.component.onToggleChoice("a")
        f.component.onSubmitAnswerClick()
        advanceUntilIdle()
        assertIs<TournamentNotice.Pending>(f.component.state.value.notice)

        // Матч закрыли: поздняя квитанция по прежнему матчу применена не будет.
        f.component.onCloseMatchSession()
        advanceUntilIdle()
        val answerIntent =
            TournamentSessionIntent.Answer(
                sessionId = matchSessionId,
                questionIndex = 0,
                questionId = questionId,
                answerPayload = buildJsonObject { put("type", "single-choice"); put("selected", "a") },
            )
        f.deliver(answerIntent, status = "ANSWER_ACCEPTED", questionIndex = 1)
        f.repository.readSessionResult = Result.success(readAnswer(1))
        f.outboxStore.markDelivered()
        advanceUntilIdle()

        assertNull(f.component.state.value.play)
        assertNull(f.component.state.value.sessionId)
        assertIs<TournamentNotice.NotLoaded>(f.component.state.value.notice)
    }

    @Test
    fun `a re-read that returns the same question keeps the collected selection`() = runTest(dispatcher) {
        val f = Fixture()
        openSession(f)
        f.component.onToggleChoice("a")
        f.repository.readSessionResult = Result.success(readAnswer(0))

        f.component.onRecheckClick()
        advanceUntilIdle()

        assertEquals(listOf("a"), requireNotNull(f.component.state.value.play).selectedIds)
    }

    @Test
    fun `reopening the same match starts a fresh read of the session`() = runTest(dispatcher) {
        val f = Fixture()
        openSession(f)
        f.component.onCloseMatchSession()
        f.repository.readSessionResult = Result.success(readAnswer(1))
        f.component.onOpenMatchSession(eventId, matchId, matchSessionId)
        advanceUntilIdle()

        assertEquals(1, requireNotNull(f.component.state.value.play).questionIndex)
        assertEquals(2, f.repository.readSessionCalls.size)
        assertEquals(matchSessionId, f.repository.readSessionCalls.last().sessionId)
    }

    @Test
    fun `a failed open leaves no previous session observation running`() = runTest(dispatcher) {
        val f = Fixture()
        openSession(f)
        // Открытие другой сессии падает на чтении: контекст сменился, наблюдение прежней
        // сессии гасится вместе с ним, а не продолжает собирать чужие пометки.
        f.repository.readSessionResult = Result.failure(IllegalStateException("read fails"))
        val otherSessionId = "m_" + "cd".repeat(32)
        f.component.onOpenMatchSession(eventId, matchId, otherSessionId)
        advanceUntilIdle()
        assertIs<TournamentNotice.Retry>(f.component.state.value.notice)

        // Живой наблюдатель прежней сессии назвал бы смену аккаунта: наблюдателя нет —
        // честное состояние неудавшегося открытия остаётся.
        f.currentSession = OutboxSession("owner-2", 2)
        f.outboxStore.markDelivered()
        advanceUntilIdle()

        assertIs<TournamentNotice.Retry>(f.component.state.value.notice)
    }
}
