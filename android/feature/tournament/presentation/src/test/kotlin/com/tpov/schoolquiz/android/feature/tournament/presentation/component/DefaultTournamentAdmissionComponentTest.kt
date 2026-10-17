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
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentDownloadRecord
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEscrowView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeView
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Маршрут допуска (история 5.10): create-only запись скачивания с серверным флагом повтора под
 * одной личностью операции, честная витрина окна по серверному «сейчас» и честные отказы.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentAdmissionComponentTest {
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

    private fun component(
        repository: FakeTournamentRepository,
        sessions: OutboxSessionProvider = OutboxSessionProvider { session },
    ): DefaultTournamentAdmissionComponent =
        DefaultTournamentAdmissionComponent(
            context = DefaultComponentContext(lifecycle),
            repository = repository,
            sessions = sessions,
            dispatcher = dispatcher,
            ids = { "generated_id_0001" },
        )

    private fun event(
        startsAtMs: Long,
        endsAtMs: Long,
        sourceKind: String = "COURSE",
        sourceId: String = "course-1",
    ): TournamentEventView =
        TournamentEventView(
            eventId = "event_admission_0000001",
            organizerUid = "owner-2",
            status = "QUALIFICATION",
            cancelReason = null,
            source = TournamentSourceRef(sourceKind, sourceId),
            qualification = TournamentQualificationView(startsAtMs, endsAtMs, 3, 20, 1),
            prizes = TournamentPrizeView(1, listOf(TournamentPrizeRow(1, 5)), 5),
            escrow = TournamentEscrowView(5, 5, 0, 0),
            progress = null,
            matches = emptyList(),
        )

    @Test
    fun `download record surfaces the replayed flag under one operation identity`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        val component = component(repository)
        component.onOpenEvent(event(0L, 100L), serverNowMs = 50L)

        repository.recordDownloadResult =
            Result.success(TournamentDownloadRecord("course-1", downloadedAtMs = 50L, replayed = false))
        component.onRecordDownloadClick()
        advanceUntilIdle()

        assertFalse(requireNotNull(component.state.value.download).replayed)

        // Повтор — та же операция, сервер отвечает stored-фактом с флагом, второго эффекта нет.
        repository.recordDownloadResult =
            Result.success(TournamentDownloadRecord("course-1", downloadedAtMs = 50L, replayed = true))
        component.onRecordDownloadClick()
        advanceUntilIdle()

        val requests = repository.recordDownloadCalls
        assertEquals(2, requests.size)
        assertEquals(requests[0].operationId, requests[1].operationId)
        assertEquals("course-1", requests[0].courseId)
        assertTrue(requireNotNull(component.state.value.download).replayed)
    }

    @Test
    fun `outside window is displayed from server facts and stays distinct from a refusal`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        val component = component(repository)

        component.onOpenEvent(event(startsAtMs = 100L, endsAtMs = 200L), serverNowMs = 250L)
        assertTrue(component.state.value.outsideWindow)

        component.onOpenEvent(event(startsAtMs = 100L, endsAtMs = 200L), serverNowMs = 150L)
        assertFalse(component.state.value.outsideWindow)

        // Старт в окне без основания — серверный отказ, а не витрина окна.
        repository.recordDownloadResult = Result.failure(refused("NO_ADMISSION_BASIS"))
        component.onRecordDownloadClick()
        advanceUntilIdle()

        val notice = assertIs<TournamentNotice.Refused>(component.state.value.notice)
        assertEquals("NO_ADMISSION_BASIS", notice.serverReason)
    }

    @Test
    fun `a lesson source hides the download action and unknown course refuses honestly`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        val component = component(repository)
        component.onOpenEvent(event(0L, 100L, sourceKind = "ARENA_LESSON"), serverNowMs = 50L)

        assertFalse(component.state.value.downloadFitsEvent)

        val courseComponent = component(repository)
        courseComponent.onOpenEvent(event(0L, 100L), serverNowMs = 50L)
        repository.recordDownloadResult = Result.failure(refused("INVALID_ARGUMENT"))
        courseComponent.onRecordDownloadClick()
        advanceUntilIdle()

        assertIs<TournamentNotice.Refused>(courseComponent.state.value.notice)
    }

    @Test
    fun `no account says so instead of calling the boundary`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        val component = component(repository, sessions = OutboxSessionProvider { null })
        component.onOpenEvent(event(0L, 100L), serverNowMs = 50L)

        component.onRecordDownloadClick()
        advanceUntilIdle()

        assertIs<TournamentNotice.AccountUnavailable>(component.state.value.notice)
        assertTrue(repository.recordDownloadCalls.isEmpty())
    }

    @Test
    fun `an in-flight record for another course is not applied to the newly opened event`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        val component = component(repository)
        component.onOpenEvent(event(0L, 100L), serverNowMs = 50L)
        repository.recordDownloadResult =
            Result.success(TournamentDownloadRecord("course-1", downloadedAtMs = 50L, replayed = false))

        // Запрос курса X ещё летит, когда открыли событие курса Y.
        component.onRecordDownloadClick()
        component.onOpenEvent(event(0L, 100L, sourceId = "course-2"), serverNowMs = 50L)
        advanceUntilIdle()

        // Запись курса X чужому событию не достаётся: у события Y своя запись скачивания.
        assertNull(component.state.value.download)
        assertFalse(component.state.value.busy)
    }
}
