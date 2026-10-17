package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeTournamentRepository
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.refused
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEscrowView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventRead
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentQualificationView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRankingRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRankingView
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Маршрут просмотра (история 5.10): страницы listEvents с курсором, чтение события с рейтингом и
 * честные состояния страницы (испорченная страница — повтор, а не краш).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentBrowserComponentTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var lifecycle: LifecycleRegistry
    private val session = OutboxSession("owner-1", 1)

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

    private fun component(repository: FakeTournamentRepository): DefaultTournamentBrowserComponent =
        DefaultTournamentBrowserComponent(
            context = DefaultComponentContext(lifecycle),
            repository = repository,
            sessions = OutboxSessionProvider { session },
            dispatcher = dispatcher,
        )

    private fun event(
        eventId: String,
        ranking: TournamentRankingView? = null,
    ): TournamentEventView =
        TournamentEventView(
            eventId = eventId,
            organizerUid = "owner-1",
            status = "BRACKET",
            cancelReason = null,
            source = TournamentSourceRef("COURSE", "course-1"),
            qualification = TournamentQualificationView(0L, 10L, 3, 20, 1),
            prizes = TournamentPrizeView(1, listOf(TournamentPrizeRow(1, 5)), 5),
            escrow = TournamentEscrowView(10, 10, 0, 0),
            progress = null,
            matches = emptyList(),
            ranking = ranking,
        )

    @Test
    fun `paging keeps the cursor context and refresh restarts it`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        val component = component(repository)

        repository.listEventsResult =
            Result.success(
                TournamentEventPage(
                    events = listOf(event("event_page_00000001")),
                    hasMore = true,
                    nextEventId = "event_page_00000001",
                    serverNowMs = 1L,
                ),
            )
        component.onRefreshClick()
        advanceUntilIdle()

        assertTrue(component.state.value.canLoadMore)
        repository.listEventsResult =
            Result.success(
                TournamentEventPage(
                    events = listOf(event("event_page_00000002")),
                    hasMore = false,
                    nextEventId = null,
                    serverNowMs = 2L,
                ),
            )
        component.onLoadMoreClick()
        advanceUntilIdle()

        val requests = repository.listEventsCalls
        assertEquals(2, requests.size)
        assertEquals(null, requests[0].afterEventId)
        assertEquals("event_page_00000001", requests[1].afterEventId)
        assertEquals(listOf("event_page_00000001", "event_page_00000002"), component.state.value.events.map { it.eventId })
        assertEquals(false, component.state.value.canLoadMore)

        // Перечитывание сбрасывает курсор осознанно: первая страница, а не хвост.
        repository.listEventsResult =
            Result.success(
                TournamentEventPage(
                    events = listOf(event("event_page_00000001")),
                    hasMore = true,
                    nextEventId = "event_page_00000001",
                    serverNowMs = 1L,
                ),
            )
        component.onRefreshClick()
        advanceUntilIdle()
        assertEquals(3, requests.size)
        assertEquals(null, requests[2].afterEventId)
        assertEquals(listOf("event_page_00000001"), component.state.value.events.map { it.eventId })
    }

    @Test
    fun `detail read carries the published ranking rows`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        val component = component(repository)
        val ranking =
            TournamentRankingView(
                totalRanked = 2,
                rows =
                    listOf(
                        TournamentRankingRow("alice", 1, ratingPercent = null, averagePercent = null),
                        TournamentRankingRow("bob", 2, ratingPercent = 91.5, averagePercent = 88.0),
                    ),
            )
        repository.readEventResult =
            Result.success(TournamentEventRead(event = event("event_ranked_00000001", ranking), serverNowMs = 5L))

        component.onOpenEvent("event_ranked_00000001")
        advanceUntilIdle()

        val detail = requireNotNull(component.state.value.detail)
        assertEquals(ranking, detail.event.ranking)
        assertEquals(5L, detail.serverNowMs)

        component.onCloseEvent()
        assertNull(component.state.value.detail)
    }

    @Test
    fun `closing the detail while the read is in flight keeps it closed`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        repository.readEventResult =
            Result.success(TournamentEventRead(event = event("event_ranked_00000001"), serverNowMs = 5L))
        val component = component(repository)

        // Чтение детали ещё летит, когда деталь закрыли: ответ не переоткрывает её.
        component.onOpenEvent("event_ranked_00000001")
        component.onCloseEvent()
        advanceUntilIdle()

        assertNull(component.state.value.detail)
        assertFalse(component.state.value.busy)
    }

    @Test
    fun `a failed page keeps what was loaded and refuses honestly`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        val component = component(repository)
        repository.listEventsResult =
            Result.success(
                TournamentEventPage(listOf(event("event_page_00000001")), hasMore = false, nextEventId = null, serverNowMs = 1L),
            )
        component.onRefreshClick()
        advanceUntilIdle()

        repository.listEventsResult = Result.failure(refused("INVALID_ARGUMENT"))
        repository.readEventResult = Result.failure(refused("INVALID_ARGUMENT"))
        component.onOpenEvent("event_ranked_00000001")
        advanceUntilIdle()

        val notice = assertIs<TournamentNotice.Refused>(component.state.value.notice)
        assertEquals("INVALID_ARGUMENT", notice.serverReason)
        assertTrue(component.state.value.detail == null)
        // Страница не поехала — уже показанное не исчезает.
        assertEquals(1, component.state.value.events.size)
    }
}
