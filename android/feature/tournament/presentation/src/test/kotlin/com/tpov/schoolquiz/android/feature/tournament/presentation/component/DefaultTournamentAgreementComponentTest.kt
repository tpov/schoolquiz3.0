package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeTournamentRepository
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.refused
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentRefusal
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.operationView
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingParticipant
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingSlot
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperateRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentReadySignal
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Согласование слота матча (история 5.11): чтение встречи строит состояние обоих участников
 * поимённо, восстановление при повторном входе идёт тем же чтением, границы и неизменность
 * согласованного слота отказывают словами сервера, готовность принимает сервер в своём окне.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentAgreementComponentTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var lifecycle: LifecycleRegistry
    private val session = OutboxSession("owner-1", 1)

    private val eventId = "event_champ_0000000001"
    private val matchId = "match_qf_00000001"

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

    private fun meetingView(
        status: String,
        slot: Long? = null,
        agreedAt: Long? = null,
        windowEnd: Long? = null,
    ): TournamentMeetingView =
        TournamentMeetingView(
            matchId = matchId,
            status = status,
            slotStartsAtMs = slot,
            agreedAtMs = agreedAt,
            windowEndMs = windowEnd,
            startedAtMs = null,
            endsAtMs = null,
            resultDue = null,
            winnerUid = null,
            loserUid = null,
            technicalForfeit = null,
        )

    private fun page(
        status: String = "WAITING_FOR_AGREEMENT",
        offers: List<TournamentMeetingSlot> = emptyList(),
        confirmations: List<TournamentMeetingSlot> = emptyList(),
        ready: List<TournamentReadySignal> = emptyList(),
        slot: Long? = null,
        windowEnd: Long? = null,
        hasMoreOffers: Boolean = false,
        nextOfferId: String? = null,
    ): TournamentMeetingPage =
        TournamentMeetingPage(
            eventId = eventId,
            meeting = meetingView(status, slot = slot, agreedAt = slot?.let { 900L }, windowEnd = windowEnd),
            schedulingDeadlineMs = 5_000L,
            stageStartsAtMs = 0L,
            stageEndsAtMs = 10_000L,
            participants = listOf(TournamentMeetingParticipant("owner-1", 1), TournamentMeetingParticipant("owner-2", 2)),
            offers = offers,
            hasMoreOffers = hasMoreOffers,
            nextOfferId = nextOfferId,
            confirmations = confirmations,
            hasMoreConfirmations = false,
            nextConfirmationId = null,
            ready = ready,
            serverNowMs = 1_000L,
        )

    private fun fixture(ids: () -> String = { "generated_id_0001" }): Fixture = Fixture(ids)

    private inner class Fixture(ids: () -> String = { "generated_id_0001" }) {
        val repository = FakeTournamentRepository()
        val component =
            DefaultTournamentAgreementComponent(
                context = DefaultComponentContext(lifecycle),
                repository = repository,
                sessions = OutboxSessionProvider { session },
                dispatcher = dispatcher,
                ids = ids,
            )
    }

    private suspend fun TestScope.open(f: Fixture) {
        f.repository.readMeetingResult = Result.success(page())
        f.component.onOpenMatch(eventId, matchId)
        advanceUntilIdle()
    }

    @Test
    fun `proposing a slot renders the offer per author and re-reads the meeting`() = runTest(dispatcher) {
        val f = fixture()
        // Открытие читает встречу пустой; после предложения перечитывает — уже с предложением.
        f.repository.readMeetingResult =
            Result.success(page())
        f.component.onOpenMatch(eventId, matchId)
        advanceUntilIdle()
        f.repository.operateResult = Result.success(operationView())
        f.repository.readMeetingResult =
            Result.success(
                page(
                    offers = listOf(TournamentMeetingSlot("offer_a", "owner-1", 4_000L, 900L)),
                ),
            )
        f.component.onSlotStartsAtChange(" 4000 ")
        f.component.onProposeClick()
        advanceUntilIdle()

        val propose = f.repository.operateCalls.single() as TournamentOperateRequest.Propose
        assertEquals(eventId, propose.eventId)
        assertEquals(matchId, propose.matchId)
        assertEquals(4_000L, propose.slotStartsAtMs)
        assertEquals(2, f.repository.readMeetingCalls.size)
        assertEquals("offer_a", f.component.state.value.meeting?.offers?.single()?.id)
        assertEquals("owner-1", f.component.state.value.meeting?.offers?.single()?.uid)
        assertIs<TournamentNotice.Ready>(f.component.state.value.notice)
    }

    @Test
    fun `re-entry rebuilds the same server facts by reading`() = runTest(dispatcher) {
        val f = fixture()
        val withOffer =
            page(
                offers = listOf(TournamentMeetingSlot("offer_a", "owner-1", 4_000L, 900L)),
                confirmations = listOf(TournamentMeetingSlot("conf_b", "owner-2", 4_000L, 950L)),
                ready = listOf(TournamentReadySignal("owner-2", 4_050L)),
            )
        f.repository.readMeetingResult = Result.success(withOffer)
        f.component.onOpenMatch(eventId, matchId)
        advanceUntilIdle()
        val first = f.component.state.value.meeting

        // Повторный вход — то же чтение: состояния обоих участников совпадают, черновиков нет.
        f.component.onCloseMatch()
        f.repository.readMeetingResult = Result.success(withOffer)
        f.component.onOpenMatch(eventId, matchId)
        advanceUntilIdle()

        assertEquals(first, f.component.state.value.meeting)
        assertEquals("", f.component.state.value.slotInput)
    }

    @Test
    fun `proposing after agreement is refused at the boundary and the agreed slot stays`() = runTest(dispatcher) {
        val f = fixture()
        f.repository.readMeetingResult =
            Result.success(page(status = "WAITING_FOR_READY", slot = 4_000L, windowEnd = 7_600_000L))
        f.component.onOpenMatch(eventId, matchId)
        advanceUntilIdle()
        f.repository.operateResult = Result.failure(refused("FAILED_PRECONDITION"))
        // Перечитывание после отказа возвращает ту же публикацию: слот неизменен.
        f.component.onSlotStartsAtChange("5000")
        f.component.onProposeClick()
        advanceUntilIdle()

        val notice = assertIs<TournamentNotice.Refused>(f.component.state.value.notice)
        assertEquals(TournamentRefusal.MEETING_BOUNDARY, notice.reason)
        assertEquals("FAILED_PRECONDITION", notice.serverReason)
        assertEquals(4_000L, f.component.state.value.meeting?.meeting?.slotStartsAtMs)
        assertEquals("WAITING_FOR_READY", f.component.state.value.meeting?.meeting?.status)
    }

    @Test
    fun `a retried propose keeps its operation identity and a new slot gets a fresh one`() = runTest(dispatcher) {
        var counter = 0
        val f = fixture(ids = { "generated_id_%04d".format(counter++) })
        open(f)
        f.repository.operateResult = Result.failure(refused("FAILED_PRECONDITION"))
        f.component.onSlotStartsAtChange("4000")
        f.component.onProposeClick()
        advanceUntilIdle()
        f.component.onProposeClick()
        advanceUntilIdle()

        val first = f.repository.operateCalls[0] as TournamentOperateRequest.Propose
        val retry = f.repository.operateCalls[1] as TournamentOperateRequest.Propose
        assertEquals(first.operationId, retry.operationId, "повтор попытки должен ехать тем же operationId")

        f.repository.operateResult =
            Result.success(operationView())
        f.component.onSlotStartsAtChange("4100")
        f.component.onProposeClick()
        advanceUntilIdle()
        val another = f.repository.operateCalls.last() as TournamentOperateRequest.Propose
        assertTrue(another.operationId != first.operationId, "новый слот — новая личность операции")
        assertEquals(4_100L, another.slotStartsAtMs)
    }

    @Test
    fun `confirm rides the offer's slot when the input is empty`() = runTest(dispatcher) {
        val f = fixture()
        f.repository.readMeetingResult =
            Result.success(page(offers = listOf(TournamentMeetingSlot("offer_a", "owner-1", 4_000L, 900L))))
        f.component.onOpenMatch(eventId, matchId)
        advanceUntilIdle()
        f.repository.operateResult =
            Result.success(operationView())
        f.repository.readMeetingResult =
            Result.success(
                page(
                    status = "WAITING_FOR_READY",
                    slot = 4_000L,
                    confirmations = listOf(TournamentMeetingSlot("conf_a", "owner-1", 4_000L, 950L)),
                ),
            )
        f.component.onConfirmClick(4_000L)
        advanceUntilIdle()

        val confirm = f.repository.operateCalls.single() as TournamentOperateRequest.Confirm
        assertEquals(4_000L, confirm.slotStartsAtMs)
        assertEquals("WAITING_FOR_READY", f.component.state.value.meeting?.meeting?.status)
    }

    @Test
    fun `row confirm sends the row slot and never the stale draft input`() = runTest(dispatcher) {
        val f = fixture()
        f.repository.readMeetingResult =
            Result.success(page(offers = listOf(TournamentMeetingSlot("offer_a", "owner-2", 4_000L, 900L))))
        f.component.onOpenMatch(eventId, matchId)
        advanceUntilIdle()
        f.repository.operateResult = Result.success(operationView())
        // Черновой ввод остался от прежней мысли: строка подтверждает свой слот, не ввод.
        f.component.onSlotStartsAtChange("999")
        f.component.onConfirmClick(4_000L)
        advanceUntilIdle()

        val confirm = f.repository.operateCalls.single() as TournamentOperateRequest.Confirm
        assertEquals(4_000L, confirm.slotStartsAtMs)
    }

    @Test
    fun `opening another match leaves the previous play session behind`() = runTest(dispatcher) {
        val f = fixture()
        open(f)
        val sessionView =
            TournamentSessionView(
                sessionId = "m_" + "ab".repeat(32),
                eventId = eventId,
                kind = "MATCH",
                roundNumber = null,
                matchId = matchId,
                status = "STARTED",
                questionIndex = 0,
                startedAtMs = 4_100L,
                endsAtMs = 5_000L,
                questionStartedAtMs = 4_100L,
                questionEndsAtMs = 4_200L,
                window = null,
            )
        f.repository.operateResult =
            Result.success(
                operationView(status = "STARTED", sessions = listOf(sessionView), serverNowMs = 4_100L),
            )
        f.component.onReadyClick()
        advanceUntilIdle()
        assertEquals("m_" + "ab".repeat(32), f.component.state.value.playSessionId)

        // Открытие матча B — новый контекст: своя сессия матча A поверхности B не достаётся.
        f.repository.readMeetingResult = Result.success(page())
        f.component.onOpenMatch(eventId, "match_qf_00000002")
        advanceUntilIdle()

        assertNull(f.component.state.value.playSessionId)
        assertEquals("match_qf_00000002", f.component.state.value.matchId)
        assertEquals("", f.component.state.value.slotInput)
    }

    @Test
    fun `has more without a cursor is the honest end, not a cursorless re-read`() = runTest(dispatcher) {
        val f = fixture()
        f.repository.readMeetingResult =
            Result.success(
                page(
                    offers = listOf(TournamentMeetingSlot("offer_a", "owner-1", 4_000L, 900L)),
                    hasMoreOffers = true,
                    nextOfferId = null,
                ),
            )
        f.component.onOpenMatch(eventId, matchId)
        advanceUntilIdle()

        // hasMore без курсора проецируется честным концом страницы: дочитки нет и звать её нечем.
        assertEquals(false, f.component.state.value.meeting?.hasMoreOffers)
        f.component.onLoadMoreClick()
        advanceUntilIdle()
        assertEquals(1, f.repository.readMeetingCalls.size)
    }

    @Test
    fun `a failed re-read after a refusal keeps the refusal notice`() = runTest(dispatcher) {
        val f = fixture()
        open(f)
        f.repository.operateResult = Result.failure(refused("FAILED_PRECONDITION"))
        // Перечитывание после отказа само падает: сохранённый отказ оно затирать не вправе.
        f.repository.readMeetingResult = Result.failure(IllegalStateException("re-read fails"))
        f.component.onSlotStartsAtChange("5000")
        f.component.onProposeClick()
        advanceUntilIdle()

        val notice = assertIs<TournamentNotice.Refused>(f.component.state.value.notice)
        assertEquals(TournamentRefusal.MEETING_BOUNDARY, notice.reason)
    }

    @Test
    fun `confirming a slot the stale page never showed is refused and re-reads the fresh truth`() = runTest(dispatcher) {
        val f = fixture()
        // Страница прочитана до того, как встреча увидела предложение: на ней предложений нет.
        f.repository.readMeetingResult = Result.success(page())
        f.component.onOpenMatch(eventId, matchId)
        advanceUntilIdle()
        f.repository.operateResult = Result.failure(refused("FAILED_PRECONDITION"))
        f.repository.readMeetingResult =
            Result.success(page(offers = listOf(TournamentMeetingSlot("offer_b", "owner-2", 4_000L, 950L))))
        f.component.onConfirmClick(4_000L)
        advanceUntilIdle()

        val confirm = f.repository.operateCalls.single() as TournamentOperateRequest.Confirm
        assertEquals(4_000L, confirm.slotStartsAtMs)
        val notice = assertIs<TournamentNotice.Refused>(f.component.state.value.notice)
        assertEquals(TournamentRefusal.MEETING_BOUNDARY, notice.reason)
        assertEquals("FAILED_PRECONDITION", notice.serverReason)
        // Перечитывание привозит свежую правду сервера поверх протухшей страницы.
        assertEquals(listOf("offer_b"), f.component.state.value.meeting?.offers?.map { it.id })
    }

    @Test
    fun `ready after the window edge is refused at the boundary`() = runTest(dispatcher) {
        val f = fixture()
        f.repository.readMeetingResult =
            Result.success(page(status = "WAITING_FOR_READY", slot = 4_000L, windowEnd = 7_600_000L))
        f.component.onOpenMatch(eventId, matchId)
        advanceUntilIdle()
        f.repository.operateResult = Result.failure(refused("FAILED_PRECONDITION"))

        f.component.onReadyClick()
        advanceUntilIdle()

        val notice = assertIs<TournamentNotice.Refused>(f.component.state.value.notice)
        assertEquals(TournamentRefusal.MEETING_BOUNDARY, notice.reason)
        assertTrue(f.component.state.value.meeting?.ready.isNullOrEmpty())
    }

    @Test
    fun `ready outside the window is refused and inside it returns the own match session`() = runTest(dispatcher) {
        val f = fixture()
        open(f)
        val sessionView =
            TournamentSessionView(
                sessionId = "m_" + "ab".repeat(32),
                eventId = eventId,
                kind = "MATCH",
                roundNumber = null,
                matchId = matchId,
                status = "STARTED",
                questionIndex = 0,
                startedAtMs = 4_100L,
                endsAtMs = 5_000L,
                questionStartedAtMs = 4_100L,
                questionEndsAtMs = 4_200L,
                window = null,
            )

        // До окна сервер отказывает границей: готовность честно не принята.
        f.repository.operateResult = Result.failure(refused("FAILED_PRECONDITION"))
        f.component.onReadyClick()
        advanceUntilIdle()
        val refusal = assertIs<TournamentNotice.Refused>(f.component.state.value.notice)
        // Поверхность согласования: FAILED_PRECONDITION сервера здесь — ветвь границы встречи.
        assertEquals(TournamentRefusal.MEETING_BOUNDARY, refusal.reason)
        assertNull(f.component.state.value.playSessionId)

        // В окне сервер принимает готовность и отвечает своей сессией: поверхность игры доступна.
        f.repository.operateResult =
            Result.success(
                operationView(
                    status = "STARTED",
                    sessions = listOf(sessionView),
                    meeting = meetingView("STARTED", slot = 4_000L, agreedAt = 900L, windowEnd = 7_600_000L),
                    serverNowMs = 4_100L,
                ),
            )
        f.repository.readMeetingResult =
            Result.success(
                page(
                    status = "STARTED",
                    slot = 4_000L,
                    windowEnd = 7_600_000L,
                    ready = listOf(TournamentReadySignal("owner-1", 4_100L), TournamentReadySignal("owner-2", 4_050L)),
                ),
            )
        f.component.onReadyClick()
        advanceUntilIdle()

        val ready = f.repository.operateCalls.last() as TournamentOperateRequest.Ready
        assertEquals(matchId, ready.matchId)
        assertEquals("m_" + "ab".repeat(32), f.component.state.value.playSessionId)
        assertEquals(2, f.component.state.value.meeting?.ready?.size)
        assertIs<TournamentNotice.Ready>(f.component.state.value.notice)
    }

    @Test
    fun `load more pulls the next offers page by cursor and appends`() = runTest(dispatcher) {
        val f = fixture()
        f.repository.readMeetingResult =
            Result.success(
                page(
                    offers = listOf(TournamentMeetingSlot("offer_a", "owner-1", 4_000L, 900L)),
                    hasMoreOffers = true,
                    nextOfferId = "offer_a",
                ),
            )
        f.component.onOpenMatch(eventId, matchId)
        advanceUntilIdle()
        f.repository.readMeetingResult =
            Result.success(
                page(
                    offers = listOf(TournamentMeetingSlot("offer_b", "owner-2", 4_100L, 950L)),
                ),
            )
        f.component.onLoadMoreClick()
        advanceUntilIdle()

        val cursorRequest = f.repository.readMeetingCalls.last()
        assertEquals("offer_a", cursorRequest.afterOfferId)
        assertEquals(
            listOf("offer_a", "offer_b"),
            f.component.state.value.meeting?.offers?.map { it.id },
        )
    }

    @Test
    fun `empty or non-numeric slot input never sends an operation`() = runTest(dispatcher) {
        val f = fixture()
        open(f)
        f.component.onSlotStartsAtChange("soon")
        f.component.onProposeClick()
        advanceUntilIdle()
        f.component.onSlotStartsAtChange("")
        f.component.onProposeClick()
        advanceUntilIdle()
        assertEquals(0, f.repository.operateCalls.size)
        assertTrue(f.repository.readMeetingCalls.size <= 1, "ввод без слота сервера не тревожит")
    }

    @Test
    fun `closing the match drops the meeting and the play session`() = runTest(dispatcher) {
        val f = fixture()
        open(f)
        f.component.onCloseMatch()
        advanceUntilIdle()
        assertNull(f.component.state.value.meeting)
        assertNull(f.component.state.value.eventId)
        assertNull(f.component.state.value.playSessionId)
        assertIs<TournamentNotice.NotLoaded>(f.component.state.value.notice)
    }
}
