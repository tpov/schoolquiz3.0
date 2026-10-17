package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeTournamentRepository
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEntrantRef
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEscrowView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventRead
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMatchRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPendingMatchRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPlacementRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentProgressView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentQualificationView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentResolvedMatchRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSourceRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
 * Сетка и итоги чемпионата (история 5.11): раунды и матчи читаются из опубликованного состояния
 * события, сетка неизменна при повторных чтениях, у завершённого события — места и правда эскроу.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentBracketComponentTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var lifecycle: LifecycleRegistry
    private val session = OutboxSession("owner-1", 1)

    private val eventId = "event_champ_0000000001"

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
        status: String,
        progress: TournamentProgressView?,
    ): TournamentEventView =
        TournamentEventView(
            eventId = eventId,
            organizerUid = "owner-2",
            status = status,
            cancelReason = null,
            source = TournamentSourceRef("COURSE", "course-1"),
            qualification = TournamentQualificationView(0L, 100L, 3, 20, 1),
            prizes = TournamentPrizeView(1, listOf(TournamentPrizeRow(1, 5)), 5),
            escrow = TournamentEscrowView(reservedBoxes = 5, remainingBoxes = 0, paidBoxes = 4, returnedBoxes = 1),
            progress = progress,
            matches =
                listOf(
                    TournamentMatchRow("match_qf_00000001", 1, 0L, 100L, 60L, 40L),
                    TournamentMatchRow("match_qf_00000002", 1, 0L, 100L, 60L, 40L),
                ),
        )

    private fun fixture(): Fixture =
        Fixture().also {
            it.repository.listEventsResult =
                Result.success(
                    TournamentEventPage(listOf(event("BRACKET", null)), hasMore = false, nextEventId = null, serverNowMs = 1L),
                )
        }

    private inner class Fixture {
        val repository = FakeTournamentRepository()
        val component =
            DefaultTournamentBracketComponent(
                context = DefaultComponentContext(lifecycle),
                repository = repository,
                sessions = OutboxSessionProvider { session },
                dispatcher = dispatcher,
            )
    }

    @Test
    fun `bracket view renders rounds and matches from the published progress`() = runTest(dispatcher) {
        val f = fixture()
        val progress =
            TournamentProgressView(
                status = "ACTIVE",
                resolved =
                    listOf(
                        TournamentResolvedMatchRow("match_qf_00000001", "PLAYED", "owner-1", "owner-2", technicalForfeit = false),
                    ),
                pending =
                    listOf(
                        TournamentPendingMatchRow("match_qf_00000002", listOf(TournamentEntrantRef("owner-3", 3), TournamentEntrantRef("owner-4", 4))),
                    ),
                placements = emptyList(),
            )
        f.repository.readEventResult = Result.success(TournamentEventRead(event("BRACKET", progress), serverNowMs = 1L))
        f.component.onRefreshClick()
        advanceUntilIdle()
        f.component.onOpenEvent(eventId)
        advanceUntilIdle()

        val detail = requireNotNull(f.component.state.value.detail)
        assertEquals("BRACKET", detail.event.status)
        assertEquals(2, detail.event.matches.size)
        assertEquals("owner-1", detail.event.progress?.resolved?.single()?.winnerUid)
        assertEquals("owner-3", detail.event.progress?.pending?.single()?.entrants?.first()?.uid)
        assertTrue(detail.event.progress?.placements.isNullOrEmpty())
        assertIs<TournamentNotice.Ready>(f.component.state.value.notice)
    }

    @Test
    fun `bracket is immutable across re-reads of the same publication`() = runTest(dispatcher) {
        val f = fixture()
        val progress =
            TournamentProgressView(
                status = "ACTIVE",
                resolved = listOf(TournamentResolvedMatchRow("match_qf_00000001", "PLAYED", "owner-1", "owner-2", technicalForfeit = false)),
                pending = emptyList(),
                placements = emptyList(),
            )
        f.repository.readEventResult = Result.success(TournamentEventRead(event("BRACKET", progress), serverNowMs = 1L))
        f.component.onOpenEvent(eventId)
        advanceUntilIdle()
        val first = f.component.state.value.detail

        f.component.onCloseEvent()
        f.component.onOpenEvent(eventId)
        advanceUntilIdle()

        assertEquals(first, f.component.state.value.detail)
    }

    @Test
    fun `finished event carries terminal placements and escrow truth`() = runTest(dispatcher) {
        val f = fixture()
        val progress =
            TournamentProgressView(
                status = "FINISHED",
                resolved = emptyList(),
                pending = emptyList(),
                placements =
                    listOf(
                        TournamentPlacementRow("owner-1", 1, "champion", technicalForfeit = false),
                        TournamentPlacementRow("owner-2", 2, "runner-up", technicalForfeit = false),
                    ),
            )
        f.repository.readEventResult = Result.success(TournamentEventRead(event("FINISHED", progress), serverNowMs = 1L))
        f.component.onOpenEvent(eventId)
        advanceUntilIdle()

        val detail = requireNotNull(f.component.state.value.detail)
        assertEquals("FINISHED", detail.event.status)
        assertEquals(
            listOf(1L to "owner-1", 2L to "owner-2"),
            detail.event.progress?.placements?.map { it.rank to it.uid },
        )
        assertEquals("champion", detail.event.progress?.placements?.first()?.slot)
        // Терминальный эскроу называет сервер: остаток нулевой, выплаты и возврат — факты.
        assertEquals(0L, detail.event.escrow.remainingBoxes)
        assertEquals(4L, detail.event.escrow.paidBoxes)
        assertEquals(1L, detail.event.escrow.returnedBoxes)
    }

    @Test
    fun `closing the bracket drops the detail without inventing state`() = runTest(dispatcher) {
        val f = fixture()
        f.repository.readEventResult =
            Result.success(TournamentEventRead(event("BRACKET", TournamentProgressView("ACTIVE", emptyList(), emptyList(), emptyList())), serverNowMs = 1L))
        f.component.onOpenEvent(eventId)
        advanceUntilIdle()
        f.component.onCloseEvent()

        assertNull(f.component.state.value.detail)
        assertIs<TournamentNotice.NotLoaded>(f.component.state.value.notice)
    }

    @Test
    fun `closing the bracket while the read is in flight does not reopen the detail`() = runTest(dispatcher) {
        val f = fixture()
        f.repository.readEventResult =
            Result.success(TournamentEventRead(event("BRACKET", TournamentProgressView("ACTIVE", emptyList(), emptyList(), emptyList())), serverNowMs = 1L))
        // Открытие принято, чтение ещё не летело: закрытие обгоняет его.
        f.component.onOpenEvent(eventId)
        f.component.onCloseEvent()
        advanceUntilIdle()

        // Опоздавшее чтение закрытую сетку заново не открывает.
        assertNull(f.component.state.value.detail)
        assertIs<TournamentNotice.NotLoaded>(f.component.state.value.notice)
    }

    @Test
    fun `a refresh refused by busy does not lose the load-more cursor`() = runTest(dispatcher) {
        val f = fixture()
        f.repository.listEventsResult =
            Result.success(
                TournamentEventPage(
                    listOf(event("BRACKET", null)),
                    hasMore = true,
                    nextEventId = eventId,
                    serverNowMs = 1L,
                ),
            )
        // Первая загрузка едет; повторный refresh занятостью отклонён — курсор его не теряет.
        f.component.onRefreshClick()
        f.component.onRefreshClick()
        advanceUntilIdle()

        f.repository.listEventsResult =
            Result.success(
                TournamentEventPage(
                    listOf(event("BRACKET", null)),
                    hasMore = false,
                    nextEventId = null,
                    serverNowMs = 1L,
                ),
            )
        f.component.onLoadMoreClick()
        advanceUntilIdle()

        assertEquals(eventId, f.repository.listEventsCalls.last().afterEventId)
        assertEquals(2, f.repository.listEventsCalls.size)
    }
}
