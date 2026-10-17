package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeOutboxStore
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeReceiptStore
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeTournamentRepository
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.operationView
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.refused
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
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEscrowView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventListRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventRead
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperationView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperateRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestion
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestionPayload
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestionRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentQualificationView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSourceRef
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
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Участие в квалификации (история 5.10): старт раунда под постоянной личностью попытки, ответ
 * только из очереди 5.9 (квитанция приёмника — единственный источник факта доставки), READY_ACK
 * в открытое окно после доставки, честный опоздавший ответ и честные отказы допуска.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentQualificationComponentTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var lifecycle: LifecycleRegistry
    private val session = OutboxSession("owner-1", 1)

    private val sessionId = "q_" + "ab".repeat(32)
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

    private fun event(
        eventId: String = "event_play_0000000001",
    ): TournamentEventView =
        TournamentEventView(
            eventId = eventId,
            organizerUid = "owner-2",
            status = "QUALIFICATION",
            cancelReason = null,
            source = TournamentSourceRef("COURSE", "course-1"),
            qualification = TournamentQualificationView(0L, 100L, 3, 20, 1),
            prizes = TournamentPrizeView(1, listOf(TournamentPrizeRow(1, 5)), 5),
            escrow = TournamentEscrowView(5, 5, 0, 0),
            progress = null,
            matches = emptyList(),
        )

    private fun sessionView(
        questionIndex: Int,
        status: String = "STARTED",
    ): TournamentSessionView =
        TournamentSessionView(
            sessionId = sessionId,
            eventId = "event_play_0000000001",
            kind = "QUALIFICATION",
            roundNumber = 1,
            matchId = null,
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
            questionId = questionId,
            type = "SingleChoice",
            difficulty = "EASY",
            contentVersion = 1,
            maxDurationMs = 21_000L,
            publicPayload =
                TournamentPublicQuestionPayload(
                    type = "SingleChoiceRedacted",
                    id = questionId,
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

    private suspend fun fixture(
        ids: () -> String = { "generated_id_0001" },
    ): Quad =
        Quad(ids).also { it.repository.readEventResult = Result.success(TournamentEventRead(event(), serverNowMs = 10L)) }

    private inner class Quad(ids: () -> String = { "generated_id_0001" }) {
        val repository = FakeTournamentRepository()
        val outboxStore = FakeOutboxStore()
        val receiptStore = FakeReceiptStore()
        val outbox =
            TournamentSessionOutboxRepository(
                outbox = outboxStore,
                receipts = receiptStore,
                sessions = OutboxSessionProvider { session },
                parser = TournamentSessionReceiptParser(TournamentSessionWireParser(TournamentEventWireParser(), TournamentMeetingWireParser())),
                clock = { 1L },
            )
        val component =
            DefaultTournamentQualificationComponent(
                context = DefaultComponentContext(lifecycle),
                repository = repository,
                outbox = outbox,
                sessions = OutboxSessionProvider { session },
                dispatcher = dispatcher,
                ids = ids,
                nowMs = { 1L },
            )

        /** Квитанция приёмника для намерения: конверт {replayed, result} с тем же operationId. */
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
                            put("sessionId", sessionId)
                            put("eventId", "event_play_0000000001")
                            put("kind", "QUALIFICATION")
                            put("roundNumber", 1)
                            put("status", "STARTED")
                            put("questionIndex", questionIndex)
                            put("startedAtMs", 0)
                            put("endsAtMs", 100)
                            put("questionStartedAtMs", 0)
                            put("questionEndsAtMs", 50)
                        },
                    )
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
                    if (intent is TournamentSessionIntent.ReadyAck) {
                        // Квитанция готовности обязана ехать в ответе READY_ACK (ONLINE-O2 §4).
                        put(
                            "readyAck",
                            buildJsonObject {
                                put("schemaVersion", 1)
                                put("questionIndex", intent.questionIndex)
                                put("windowId", intent.windowId)
                                put("questionId", "q000")
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
                    entityRef = TournamentSessionOutboxCodec.entityRef(sessionId),
                    payloadJson = envelope.toString(),
                    receivedAtMs = 1L,
                )
        }
    }

    private suspend fun TestScope.pickAndStart(f: Quad) {
        f.repository.readSessionResult =
            Result.success(
                TournamentOperationView(
                    schemaVersion = null,
                    operationId = null,
                    status = null,
                    serverNowMs = 10L,
                    deadlineReached = null,
                    questionDeadlineReached = null,
                    deliveredNowMs = null,
                    window = null,
                    event = null,
                    session = sessionView(0),
                    sessions = null,
                    meeting = null,
                    currentQuestion = question(0),
                    lastClosed = null,
                    readyAck = null,
                    result = null,
                ),
            )
        f.component.onPickEvent("event_play_0000000001")
        advanceUntilIdle()
        f.repository.operateResult =
            Result.success(
                TournamentOperationView(
                    schemaVersion = 1L,
                    operationId = "op_1",
                    status = "SESSION",
                    serverNowMs = 10L,
                    deadlineReached = null,
                    questionDeadlineReached = null,
                    deliveredNowMs = null,
                    window = null,
                    event = null,
                    session = sessionView(0),
                    sessions = null,
                    meeting = null,
                    currentQuestion = null,
                    lastClosed = null,
                    readyAck = null,
                    result = null,
                ),
            )
        f.component.onStartRoundClick(1)
        advanceUntilIdle()
    }

    @Test
    fun `start round plays the first question and answers through the outbox`() = runTest(dispatcher) {
        val f = fixture()
        pickAndStart(f)
        val play = requireNotNull(f.component.state.value.play)
        assertEquals(sessionId, play.sessionId)
        assertEquals(0, play.questionIndex)
        assertEquals("A?", play.question?.publicPayload?.text)

        // Ответ уезжает из очереди; квитанция приёмника несёт окно следующего вопроса.
        val answerIntent =
            TournamentSessionIntent.Answer(
                sessionId = sessionId,
                questionIndex = 0,
                questionId = questionId,
                answerPayload = buildJsonObject { put("type", "single-choice"); put("selected", "a") },
            )
        val ackIntent =
            TournamentSessionIntent.ReadyAck(
                sessionId = sessionId,
                questionIndex = 1,
                windowId = "win_" + "cd".repeat(16),
            )
        val openWindow =
            TournamentWindowView(
                windowId = ackIntent.windowId,
                completedQuestionIndex = 0,
                nextQuestionIndex = 1,
                windowSeconds = 5,
                openedAtMs = 1L,
                expiresAtMs = 101L,
                state = "OPEN",
            )
        f.deliver(answerIntent, status = "ANSWER_ACCEPTED", window = openWindow, questionIndex = 1)
        f.deliver(ackIntent, status = "READY_ACK", questionIndex = 1)
        f.repository.readSessionResult =
            Result.success(
                TournamentOperationView(
                    schemaVersion = null,
                    operationId = null,
                    status = null,
                    serverNowMs = 10L,
                    deadlineReached = null,
                    questionDeadlineReached = null,
                    deliveredNowMs = null,
                    window = null,
                    event = null,
                    session = sessionView(1),
                    sessions = null,
                    meeting = null,
                    currentQuestion = question(1),
                    lastClosed = null,
                    readyAck = null,
                    result = null,
                ),
            )

        f.component.onToggleChoice("a")
        f.component.onSubmitAnswerClick()
        advanceUntilIdle()

        // Одна очередь — два намерения: ответ и квитанция готовности в открытое окно.
        assertEquals(2, f.outboxStore.rows.size)
        // Приёмник ответил обоим: движок увозит доставленные строки (AD-4 — очередь, не архив).
        f.outboxStore.markDelivered()
        advanceUntilIdle()
        val playAfter = requireNotNull(f.component.state.value.play)
        assertEquals(1, playAfter.questionIndex)
        assertEquals(ackIntent.windowId, playAfter.readyAckWindowId)
        assertFalse(playAfter.awaitingDelivery)
    }

    @Test
    fun `late answer keeps the honest timeout marker until the server moves on`() = runTest(dispatcher) {
        val f = fixture()
        pickAndStart(f)
        val answerIntent =
            TournamentSessionIntent.Answer(
                sessionId = sessionId,
                questionIndex = 0,
                questionId = questionId,
                answerPayload = buildJsonObject { put("type", "single-choice"); put("selected", "a") },
            )
        // Сервер ещё не подвёл вопрос: чтение показывает ту же позицию — метка остаётся.
        f.deliver(answerIntent, status = "QUESTION_TIMED_OUT", questionIndex = 0)
        f.repository.readSessionResult =
            Result.success(
                TournamentOperationView(
                    schemaVersion = null,
                    operationId = null,
                    status = null,
                    serverNowMs = 10L,
                    deadlineReached = null,
                    questionDeadlineReached = null,
                    deliveredNowMs = null,
                    window = null,
                    event = null,
                    session = sessionView(0),
                    sessions = null,
                    meeting = null,
                    currentQuestion = null,
                    lastClosed = null,
                    readyAck = null,
                    result = null,
                ),
            )

        f.component.onToggleChoice("a")
        f.component.onSubmitAnswerClick()
        advanceUntilIdle()

        val play = requireNotNull(f.component.state.value.play)
        assertTrue(play.timedOut)

        // Сервер подвёл вопрос: экран показывает, где сессия, без выдуманного счёта.
        f.component.onRecheckClick()
        advanceUntilIdle()
        assertFalse(requireNotNull(f.component.state.value.play).timedOut)
    }

    @Test
    fun `a settled session shows the server result without invented time`() = runTest(dispatcher) {
        val f = fixture()
        pickAndStart(f)
        val answerIntent =
            TournamentSessionIntent.Answer(
                sessionId = sessionId,
                questionIndex = 0,
                questionId = questionId,
                answerPayload = buildJsonObject { put("type", "single-choice"); put("selected", "a") },
            )
        f.deliver(answerIntent, status = "COMPLETED", questionIndex = 0)
        f.repository.readSessionResult =
            Result.success(
                TournamentOperationView(
                    schemaVersion = null,
                    operationId = null,
                    status = null,
                    serverNowMs = 10L,
                    deadlineReached = null,
                    questionDeadlineReached = null,
                    deliveredNowMs = null,
                    window = null,
                    event = null,
                    session = sessionView(0, status = "SETTLED"),
                    sessions = null,
                    meeting = null,
                    currentQuestion = null,
                    lastClosed = null,
                    readyAck = null,
                    result =
                        com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionResult(
                            percentScore = 87.5,
                            elapsedMs = 1_000L,
                            completedAtMs = 1_000L,
                        ),
                ),
            )

        f.component.onToggleChoice("a")
        f.component.onSubmitAnswerClick()
        advanceUntilIdle()

        val play = requireNotNull(f.component.state.value.play)
        assertTrue(play.settled)
        assertEquals(87.5, play.result?.percentScore)
    }

    @Test
    fun `start refusal renders the admission basis honestly`() = runTest(dispatcher) {
        val f = fixture()
        f.component.onPickEvent("event_play_0000000001")
        advanceUntilIdle()
        f.repository.operateResult = Result.failure(refused("NO_ADMISSION_BASIS"))

        f.component.onStartRoundClick(1)
        advanceUntilIdle()

        val notice = assertIs<TournamentNotice.Refused>(f.component.state.value.notice)
        assertEquals("NO_ADMISSION_BASIS", notice.serverReason)
        assertTrue(f.outboxStore.rows.isEmpty())
        // Повтор попытки остаётся возможен: личность попытки не сгорела отказом.
        assertEquals(1, f.repository.operateCalls.size)
    }

    @Test
    fun `no delivery keeps the row honest and says pending`() = runTest(dispatcher) {
        val f = fixture()
        pickAndStart(f)

        f.component.onToggleChoice("a")
        f.component.onSubmitAnswerClick()
        advanceUntilIdle()

        assertEquals(1, f.outboxStore.rows.size)
        assertIs<TournamentNotice.Pending>(f.component.state.value.notice)
        assertTrue(requireNotNull(f.component.state.value.play).awaitingDelivery)
    }

    @Test
    fun `picking another event starts the next round with a fresh operation identity`() = runTest(dispatcher) {
        var counter = 0
        val f = fixture(ids = { "generated_id_%04d".format(counter++) })
        f.repository.readSessionResult =
            Result.success(operationView(session = sessionView(0), currentQuestion = question(0), serverNowMs = 10L))
        f.component.onPickEvent("event_play_0000000001")
        advanceUntilIdle()
        f.repository.operateResult =
            Result.success(operationView(status = "SESSION", session = sessionView(0), serverNowMs = 10L))
        f.component.onStartRoundClick(1)
        advanceUntilIdle()
        val firstStart = f.repository.operateCalls.single() as TournamentOperateRequest.StartQualification

        // Выбор другого события стирает личности попыток: START нового события не переигрывает
        // operationId чужого события — иначе сервер честно ответил бы CONFLICTING_REPLAY.
        f.repository.readEventResult =
            Result.success(TournamentEventRead(event("event_play_0000000002"), serverNowMs = 10L))
        f.component.onPickEvent("event_play_0000000002")
        advanceUntilIdle()
        f.component.onStartRoundClick(1)
        advanceUntilIdle()

        val secondStart = f.repository.operateCalls.last() as TournamentOperateRequest.StartQualification
        assertEquals("event_play_0000000002", secondStart.eventId)
        assertTrue(
            secondStart.operationId != firstStart.operationId,
            "START of the new event replayed the previous event's operation identity",
        )
        assertTrue(secondStart.attemptId != firstStart.attemptId)
        assertIs<TournamentNotice.Ready>(f.component.state.value.notice)
    }

    @Test
    fun `link token is presented to the start operation`() = runTest(dispatcher) {
        val f = fixture()
        f.repository.readSessionResult =
            Result.success(operationView(session = sessionView(0), currentQuestion = question(0), serverNowMs = 10L))
        f.component.onPickEvent("event_play_0000000001")
        advanceUntilIdle()
        // Токен ссылки вводится на поверхности старта и предъявляется только START'у.
        f.component.onLinkTokenChange(" link_token_1 ")
        f.repository.operateResult =
            Result.success(operationView(status = "SESSION", session = sessionView(0), serverNowMs = 10L))
        f.component.onStartRoundClick(1)
        advanceUntilIdle()

        val start = f.repository.operateCalls.single() as TournamentOperateRequest.StartQualification
        assertEquals("link_token_1", start.linkToken)
    }

    @Test
    fun `tapping a selected ordering row removes it from the selection`() = runTest(dispatcher) {
        val f = fixture()
        pickAndStart(f)

        f.component.onOrderTap("a")
        assertEquals(listOf("a"), requireNotNull(f.component.state.value.play).selectedIds)
        f.component.onOrderTap("b")
        assertEquals(listOf("a", "b"), requireNotNull(f.component.state.value.play).selectedIds)
        // Повторный тап по выбранной строке убирает её: полный порядок собирается
        // перестановкой, а не сбросом всего ответа.
        f.component.onOrderTap("a")
        assertEquals(listOf("b"), requireNotNull(f.component.state.value.play).selectedIds)
        f.component.onOrderTap("b")
        assertEquals(emptyList(), requireNotNull(f.component.state.value.play).selectedIds)
    }

    @Test
    fun `a re-read that returns the same question keeps the collected selection`() = runTest(dispatcher) {
        val f = fixture()
        pickAndStart(f)
        f.component.onToggleChoice("a")

        // Сервер вернул тот же вопрос (край окна, переспрос): собранный ответ не стирается.
        f.repository.readSessionResult =
            Result.success(operationView(session = sessionView(0), currentQuestion = question(0), serverNowMs = 10L))
        f.component.onRecheckClick()
        advanceUntilIdle()

        assertEquals(listOf("a"), requireNotNull(f.component.state.value.play).selectedIds)
    }

    @Test
    fun `a re-read that returns the next question clears the collected selection`() = runTest(dispatcher) {
        val f = fixture()
        pickAndStart(f)
        f.component.onToggleChoice("a")

        // Вопрос сменился: прошлая сборка не приписывается новому вопросу.
        f.repository.readSessionResult =
            Result.success(
                operationView(
                    session = sessionView(1),
                    currentQuestion = question(0).copy(questionId = "q_next"),
                    serverNowMs = 10L,
                ),
            )
        f.component.onRecheckClick()
        advanceUntilIdle()

        assertEquals(emptyList(), requireNotNull(f.component.state.value.play).selectedIds)
    }

    @Test
    fun `a late delivered receipt after the poll budget drives the answer outcome`() = runTest(dispatcher) {
        val f = fixture()
        pickAndStart(f)
        // Бюджет опроса квитанции кончился: строка честно ждёт в очереди.
        f.component.onToggleChoice("a")
        f.component.onSubmitAnswerClick()
        advanceUntilIdle()
        assertIs<TournamentNotice.Pending>(f.component.state.value.notice)
        assertTrue(requireNotNull(f.component.state.value.play).awaitingDelivery)
        assertEquals(1, f.outboxStore.rows.size)

        // Приёмник ответил, движок забрал доставленную строку — позже бюджета.
        val answerIntent =
            TournamentSessionIntent.Answer(
                sessionId = sessionId,
                questionIndex = 0,
                questionId = questionId,
                answerPayload = buildJsonObject { put("type", "single-choice"); put("selected", "a") },
            )
        f.deliver(answerIntent, status = "ANSWER_ACCEPTED")
        f.repository.readSessionResult =
            Result.success(operationView(session = sessionView(1), currentQuestion = question(1), serverNowMs = 10L))
        f.outboxStore.markDelivered()
        advanceUntilIdle()

        val play = requireNotNull(f.component.state.value.play)
        assertEquals(1, play.questionIndex)
        assertFalse(play.awaitingDelivery)
        assertIs<TournamentNotice.Ready>(f.component.state.value.notice)
        // Один ответ — одно применение: поздняя квитанция не создаёт второй операции.
        assertEquals(0, f.outboxStore.rows.size)
    }

    @Test
    fun `event picker pages with the cursor`() = runTest(dispatcher) {
        val f = fixture()
        f.repository.listEventsResult =
            Result.success(
                TournamentEventPage(
                    listOf(event(), event()),
                    hasMore = true,
                    nextEventId = "event_play_0000000001",
                    serverNowMs = 1L,
                ),
            )
        f.component.onRefreshEventsClick()
        advanceUntilIdle()

        f.repository.listEventsResult =
            Result.success(
                TournamentEventPage(listOf(event()), hasMore = false, nextEventId = null, serverNowMs = 1L),
            )
        f.component.onLoadMoreEventsClick()
        advanceUntilIdle()

        val requests = f.repository.listEventsCalls
        assertEquals(2, requests.size)
        assertEquals(null, requests[0].afterEventId)
        assertEquals("event_play_0000000001", requests[1].afterEventId)
        assertTrue(f.component.state.value.events.isNotEmpty())
        assertFalse(f.component.state.value.canLoadMore)
    }

    @Test
    fun `an unknown selection cannot be sent as an answer`() = runTest(dispatcher) {
        val f = fixture()
        pickAndStart(f)

        // Без выбора ответа отправки нет: пустая полезная нагрузка серверу не едет.
        f.component.onSubmitAnswerClick()
        advanceUntilIdle()

        assertEquals(0, f.outboxStore.rows.size)
        assertNull(f.component.state.value.play?.lastStatus)
    }

    @Test
    fun `codec rejects an out-of-range question index before the queue sees it`() {
        assertThrows(IllegalArgumentException::class.java) {
            TournamentSessionOutboxCodec.sourceId(
                TournamentSessionIntent.Advance(sessionId = sessionId, questionIndex = 20),
            )
        }
    }
}
