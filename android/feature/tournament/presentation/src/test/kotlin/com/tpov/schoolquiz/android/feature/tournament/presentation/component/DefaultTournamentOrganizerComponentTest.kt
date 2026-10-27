package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.FakeTournamentRepository
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.operationView
import com.tpov.schoolquiz.android.feature.tournament.presentation.fake.refused
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentRefusal
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentAdmissionLink
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEscrowView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperateRequest
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Маршрут организатора (история 5.10): подготовка, замороженное согласие, CREATE с постоянной
 * личностью операции и честные отказы. Повтор CREATE едет тем же operationId/eventId.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentOrganizerComponentTest {
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
        repository: TournamentCompetitionRepository,
        sessions: OutboxSessionProvider = OutboxSessionProvider { session },
        ids: () -> String = { "generated_id_0001" },
    ): DefaultTournamentOrganizerComponent =
        DefaultTournamentOrganizerComponent(
            context = DefaultComponentContext(lifecycle),
            repository = repository,
            sessions = sessions,
            dispatcher = dispatcher,
            ids = ids,
            nowMs = { 1_000L },
        )

    private fun createEventView(escrow: TournamentEscrowView): TournamentEventView =
        TournamentEventView(
            eventId = "event_created_00000001",
            organizerUid = "owner-1",
            status = "QUALIFICATION",
            cancelReason = null,
            source = TournamentSourceRef("COURSE", "course-1"),
            qualification = TournamentQualificationView(0L, 7L, 3, 20, 1),
            prizes = TournamentPrizeView(1, listOf(TournamentPrizeRow(1, 5)), 10),
            escrow = escrow,
            progress = null,
            matches = emptyList(),
        )

    @Test
    fun `prepare reads the source bank for the captured account`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        repository.prepareResult = Result.success(prepareReply("snapshot-1"))
        val component = component(repository)

        component.onSourceIdChange(" course-1 ")
        component.onPrepareClick()
        advanceUntilIdle()

        val request = repository.prepareCalls.single()
        assertEquals("owner-1", request.ownerUid)
        assertEquals("COURSE", request.source.kind)
        assertEquals("course-1", request.source.id)
        assertTrue(component.state.value.hasPrepare)
        assertIs<TournamentNotice.Ready>(component.state.value.notice)
    }

    @Test
    fun `consent freezes terms and create never regenerates the operation identity`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        val escrow = TournamentEscrowView(reservedBoxes = 10, remainingBoxes = 10, paidBoxes = 0, returnedBoxes = 0)
        val event = createEventView(escrow)
        repository.prepareResult = Result.success(prepareReply("snapshot-1"))
        repository.operateResult = Result.success(operationView(status = "CREATED", event = event, serverNowMs = 1L))
        val component = component(repository)

        component.onSourceIdChange("course-1")
        component.onPrepareClick()
        advanceUntilIdle()
        component.onShowConsentClick()

        val consent = requireNotNull(component.state.value.consent)
        assertEquals(CANONICAL_PRIZE_ROWS, consent.prizeRows)
        assertEquals(consent.fundBoxes, consent.prizeRows.sumOf { it.amountBoxes })
        assertEquals(10L, consent.fundBoxes)

        component.onConfirmCreate(consent.dialogId)
        advanceUntilIdle()
        assertNull(component.state.value.consent)
        assertEquals(event, component.state.value.created)
        assertEquals(escrow, component.state.value.created?.escrow)

        // Повтор CREATE — та же операция, то же событие: сервер не увидит второй резерв.
        component.onRetryCreateClick()
        advanceUntilIdle()

        val creates = repository.operateCalls.map { it as TournamentOperateRequest.Create }
        assertEquals(2, creates.size)
        assertEquals(creates[0].operationId, creates[1].operationId)
        assertEquals(creates[0].eventId, creates[1].eventId)
        assertEquals(consent.prizeRows, creates[0].prizeRows)
        assertEquals(consent.fundBoxes, creates[0].fundedBoxes)
    }

    @Test
    fun `underfunded create refuses honestly and keeps the same operation for retry`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        repository.prepareResult = Result.success(prepareReply("snapshot-1"))
        repository.operateResult = Result.failure(refused("INSUFFICIENT_BOXES"))
        val component = component(repository)

        component.onSourceIdChange("course-1")
        component.onPrepareClick()
        advanceUntilIdle()
        component.onShowConsentClick()
        component.onConfirmCreate(requireNotNull(component.state.value.consent).dialogId)
        advanceUntilIdle()

        val notice = assertIs<TournamentNotice.Refused>(component.state.value.notice)
        assertEquals(TournamentRefusal.INSUFFICIENT_BOXES, notice.reason)
        assertTrue(component.state.value.canRetryCreate)

        val legacy = Result.success(operationView(status = "CREATED", event = createEventView(TournamentEscrowView(10, 10, 0, 0))))
        repository.operateResult = legacy
        component.onRetryCreateClick()
        advanceUntilIdle()

        val creates = repository.operateCalls.map { it as TournamentOperateRequest.Create }
        assertEquals(2, creates.size)
        assertEquals(creates[0].operationId, creates[1].operationId)
        assertEquals(component.state.value.created?.escrow?.reservedBoxes, 10L)
    }

    @Test
    fun `a new create cycle starts a fresh operation identity`() = runTest(dispatcher) {
        var counter = 0
        val repository = FakeTournamentRepository()
        val escrow = TournamentEscrowView(reservedBoxes = 10, remainingBoxes = 10, paidBoxes = 0, returnedBoxes = 0)
        repository.prepareResult = Result.success(prepareReply("snapshot-1"))
        repository.operateResult =
            Result.success(operationView(status = "CREATED", event = createEventView(escrow), serverNowMs = 1L))
        val component = component(repository, ids = { "generated_id_%04d".format(counter++) })

        component.onSourceIdChange("course-1")
        component.onPrepareClick()
        advanceUntilIdle()
        component.onShowConsentClick()
        component.onConfirmCreate(requireNotNull(component.state.value.consent).dialogId)
        advanceUntilIdle()
        assertNotNull(component.state.value.created)
        val first = repository.operateCalls.single() as TournamentOperateRequest.Create

        // Новый цикл создания: созданное событие и личность прошлой операции уходят; банк
        // prepare'ится заново — успех CREATE его разобрал.
        component.onNewCreateClick()
        assertNull(component.state.value.created)
        component.onPrepareClick()
        advanceUntilIdle()
        component.onShowConsentClick()
        component.onConfirmCreate(requireNotNull(component.state.value.consent).dialogId)
        advanceUntilIdle()

        val creates = repository.operateCalls.map { it as TournamentOperateRequest.Create }
        assertEquals(2, creates.size)
        assertTrue(creates[1].operationId != creates[0].operationId, "a new create cycle replayed the old operation identity")
        assertTrue(creates[1].eventId != creates[0].eventId)
        assertNotNull(component.state.value.created)
    }

    @Test
    fun `create receipt carries the admission link token and a new cycle drops it`() = runTest(dispatcher) {
        // L4: CREATE-квитанция — единственный легальный путь токена; состояние несёт его до
        // нового цикла создания (по нему второй аккаунт стартует; ARENA_LESSON — единственная
        // основа допуска).
        val repository = FakeTournamentRepository()
        val token = "a".repeat(48)
        val escrow = TournamentEscrowView(reservedBoxes = 10, remainingBoxes = 10, paidBoxes = 0, returnedBoxes = 0)
        repository.prepareResult = Result.success(prepareReply("snapshot-1"))
        repository.operateResult =
            Result.success(
                operationView(
                    status = "CREATED",
                    event = createEventView(escrow),
                    serverNowMs = 1L,
                    admissionLink = TournamentAdmissionLink(token = token),
                ),
            )
        val component = component(repository)

        component.onSourceIdChange("course-1")
        component.onPrepareClick()
        advanceUntilIdle()
        component.onShowConsentClick()
        component.onConfirmCreate(requireNotNull(component.state.value.consent).dialogId)
        advanceUntilIdle()

        assertEquals(token, component.state.value.admissionToken)
        assertNotNull(component.state.value.created)

        // Новый цикл: показанный токен уходит вместе с созданным событием.
        component.onNewCreateClick()
        assertNull(component.state.value.admissionToken)
        assertNull(component.state.value.created)
    }

    @Test
    fun `freezing a new consent drops the stale create identity`() = runTest(dispatcher) {
        var counter = 0
        val repository = FakeTournamentRepository()
        repository.prepareResult = Result.success(prepareReply("snapshot-1"))
        repository.operateResult = Result.failure(refused("INSUFFICIENT_BOXES"))
        val component = component(repository, ids = { "generated_id_%04d".format(counter++) })

        component.onSourceIdChange("course-1")
        component.onPrepareClick()
        advanceUntilIdle()
        component.onShowConsentClick()
        component.onConfirmCreate(requireNotNull(component.state.value.consent).dialogId)
        advanceUntilIdle()
        val refusedCreate = repository.operateCalls.single() as TournamentOperateRequest.Create
        assertTrue(component.state.value.canRetryCreate)

        // Новое согласие может нести другое тело (призы/фонд): повтор под старой личностью
        // операции ответил бы CONFLICTING_REPLAY — личность замораживается заново.
        component.onShowConsentClick()
        component.onConfirmCreate(requireNotNull(component.state.value.consent).dialogId)
        advanceUntilIdle()

        val refrozen = repository.operateCalls[1] as TournamentOperateRequest.Create
        assertTrue(refrozen.operationId != refusedCreate.operationId)
        assertTrue(refrozen.eventId != refusedCreate.eventId)
    }

    @Test
    fun `account unavailable refuses to act and says so`() = runTest(dispatcher) {
        val repository = FakeTournamentRepository()
        val component = component(repository, sessions = OutboxSessionProvider { null })

        component.onSourceIdChange("course-1")
        component.onPrepareClick()
        advanceUntilIdle()

        assertIs<TournamentNotice.AccountUnavailable>(component.state.value.notice)
        assertTrue(repository.prepareCalls.isEmpty())
    }

    private fun prepareReply(snapshotId: String) =
        com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrepareReply(
            snapshotId = snapshotId,
            timingVersion = "v1",
            counts = com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrepareCounts(60, 20, 80),
        )
}
