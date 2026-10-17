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
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventRead
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPlacementRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentProgressView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentQualificationView
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

/**
 * Итоги чемпионата (история 5.11): места и эскроу приезжают чтением события; терминальный
 * остаток называет сервер, повторное чтение показывает ту же публикацию.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentResultsComponentTest {
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

    private inner class Fixture {
        val repository = FakeTournamentRepository()
        val component =
            DefaultTournamentResultsComponent(
                context = DefaultComponentContext(lifecycle),
                repository = repository,
                sessions = OutboxSessionProvider { session },
                dispatcher = dispatcher,
            )
    }

    private fun finishedEvent(): TournamentEventView =
        TournamentEventView(
            eventId = eventId,
            organizerUid = "owner-2",
            status = "FINISHED",
            cancelReason = null,
            source = TournamentSourceRef("COURSE", "course-1"),
            qualification = TournamentQualificationView(0L, 100L, 3, 20, 1),
            prizes = TournamentPrizeView(1, listOf(TournamentPrizeRow(1, 3), TournamentPrizeRow(2, 1)), 4),
            escrow = TournamentEscrowView(reservedBoxes = 4, remainingBoxes = 0, paidBoxes = 4, returnedBoxes = 0),
            progress =
                TournamentProgressView(
                    status = "FINISHED",
                    resolved = emptyList(),
                    pending = emptyList(),
                    placements =
                        listOf(
                            TournamentPlacementRow("owner-1", 1, "champion", technicalForfeit = false),
                            TournamentPlacementRow("owner-2", 2, "runner-up", technicalForfeit = true),
                        ),
                ),
            matches = emptyList(),
        )

    @Test
    fun `results render placements and the terminal escrow truth`() = runTest(dispatcher) {
        val f = Fixture()
        f.repository.readEventResult = Result.success(TournamentEventRead(finishedEvent(), serverNowMs = 1L))

        f.component.onOpenEvent(eventId)
        advanceUntilIdle()

        val detail = requireNotNull(f.component.state.value.detail)
        assertEquals(
            listOf("owner-1" to 1L, "owner-2" to 2L),
            detail.event.progress?.placements?.map { it.uid to it.rank },
        )
        // Терминальный эскроу: остаток нулевой — выплачено всё, возвращать нечего (факт сервера).
        val escrow = detail.event.escrow
        assertEquals(0L, escrow.remainingBoxes)
        assertEquals(4L, escrow.paidBoxes)
        assertEquals(escrow.reservedBoxes, escrow.paidBoxes + escrow.returnedBoxes + escrow.remainingBoxes)
        assertIs<TournamentNotice.Ready>(f.component.state.value.notice)
    }

    @Test
    fun `a refusal renders honestly and the results stay closed`() = runTest(dispatcher) {
        val f = Fixture()
        f.repository.readEventResult = Result.failure(refused("PERMISSION_DENIED"))

        f.component.onOpenEvent(eventId)
        advanceUntilIdle()

        val notice = assertIs<TournamentNotice.Refused>(f.component.state.value.notice)
        assertEquals("PERMISSION_DENIED", notice.serverReason)
        assertNull(f.component.state.value.detail)
    }

    @Test
    fun `reopening reads the same publication again`() = runTest(dispatcher) {
        val f = Fixture()
        f.repository.readEventResult = Result.success(TournamentEventRead(finishedEvent(), serverNowMs = 1L))
        f.component.onOpenEvent(eventId)
        advanceUntilIdle()
        val first = f.component.state.value.detail

        f.component.onClose()
        f.component.onOpenEvent(eventId)
        advanceUntilIdle()

        assertEquals(first, f.component.state.value.detail)
        assertEquals(2, f.repository.readEventCalls.size)
    }
}
