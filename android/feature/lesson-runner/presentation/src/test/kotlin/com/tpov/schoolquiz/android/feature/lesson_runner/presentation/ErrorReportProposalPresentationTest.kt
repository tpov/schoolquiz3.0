package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeAbortAttemptUseCase
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeAttemptSyncMarkRepository
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeClock
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeCompleteAttemptUseCase
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeProfileRepository
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeServerAttemptSettlementRepository
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeStartLessonAttemptUseCase
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeSubmitLessonRatingUseCase
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeSyncScheduler
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.ErrorReportSubmissionState
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.CopyEditProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ErrorReportProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopy
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitErrorReportProposalCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonProposalRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.QuoteLessonProposalFeeUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitErrorReportProposalUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Подача отчёта об ошибке как предложения (история 4.22): цитата до отправки, постановка в
 * очередь с усечёнными текстами, честное состояние кнопки. Хост без ручек (null) — строка
 * молчит, состояние не выдумывается.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ErrorReportProposalPresentationTest {
    private lateinit var lifecycle: LifecycleRegistry

    private val recordedProposals = mutableListOf<ErrorReportProposal>()
    private val recordedCopyEdits = mutableListOf<CopyEditProposal>()
    private var editCopyReply: LessonEditCopy? = null
    private var quoteReply: LessonProposalFeeQuote? =
        LessonProposalFeeQuote(kind = "REPORT", feeNolics = 0, tariffRevision = 1)

    private val fakeRepository =
        object : LessonProposalRepository {
            override suspend fun queueErrorReportProposal(proposal: ErrorReportProposal): Result<Unit> {
                recordedProposals += proposal
                return Result.success(Unit)
            }

            override suspend fun queueNewLessonProposal(proposal: NewLessonProposal): Result<Unit> =
                Result.success(Unit)

            override suspend fun queueCopyEditProposal(proposal: CopyEditProposal): Result<Unit> {
                recordedCopyEdits += proposal
                return Result.success(Unit)
            }

            override suspend fun fetchLessonEditCopy(lessonId: String): Result<LessonEditCopy> {
                val reply = editCopyReply
                    ?: return Result.failure(IllegalStateException("copy unavailable"))
                return Result.success(reply)
            }

            override suspend fun quoteProposalFee(
                kind: String,
                changedQuestions: Int,
            ): Result<LessonProposalFeeQuote> {
                val reply = quoteReply ?: return Result.failure(IllegalStateException("quote unavailable"))
                return Result.success(reply)
            }
        }

    @Before
    fun setUp() {
        lifecycle = LifecycleRegistry().apply { resume() }
    }

    @After
    fun tearDown() {
        lifecycle.stop()
        lifecycle.destroy()
    }

    private fun buildComponent(
        submit: SubmitErrorReportProposalUseCase? = submitUseCase(),
        quote: QuoteLessonProposalFeeUseCase? = quoteUseCase(),
        currentUid: () -> String? = { "user-1" },
    ): DefaultLessonRunnerRootComponent {
        val clock = FakeClock()
        val lessonRepo = FakeLessonRepository()
        return DefaultLessonRunnerRootComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            lessonId = LessonId("lesson-1"),
            mode = com.tpov.schoolquiz.shared.core.question_schema.Difficulty.EASY,
            useCases =
                LessonRunnerUseCases(
                    startAttempt = FakeStartLessonAttemptUseCase()::invoke,
                    completeAttempt = FakeCompleteAttemptUseCase()::invoke,
                    abortAttempt = FakeAbortAttemptUseCase()::invoke,
                    submitRating = FakeSubmitLessonRatingUseCase()::invoke,
                ),
            lessonRepository = lessonRepo,
            attemptRepository = FakeLessonAttemptRepository(),
            attemptSyncMarks = FakeAttemptSyncMarkRepository(),
            attemptSettlements = FakeServerAttemptSettlementRepository(),
            syncScheduler = FakeSyncScheduler(),
            profileRepository = FakeProfileRepository(),
            getResultAdvice = com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.GetResultAdviceUseCase(lessonRepository = lessonRepo),
            clock = clock,
            submitErrorReportProposal = submit,
            quoteProposalFee = quote,
            currentUid = currentUid,
            mainContext = Dispatchers.Unconfined,
        )
    }

    private fun submitUseCase(
        repository: LessonProposalRepository = fakeRepository,
    ): SubmitErrorReportProposalUseCase =
        SubmitErrorReportProposalUseCase(
            repository = repository,
            idProvider = { prefix -> "$prefix-test-1" },
            timestampProvider = { 42L },
        )

    private fun quoteUseCase(
        repository: LessonProposalRepository = fakeRepository,
    ): QuoteLessonProposalFeeUseCase = QuoteLessonProposalFeeUseCase(repository)

    @Test
    fun `dialog open fetches the fee quote for the report entry`() = runTest {
        val component = buildComponent()
        assertNull(component.proposalFeeQuote.first())
        component.onReportDialogOpened()
        val quote = component.proposalFeeQuote.first { it != null }
        assertEquals("REPORT", quote?.kind)
        assertEquals(0, quote?.feeNolics)
        assertEquals(ErrorReportSubmissionState.Idle, component.errorReportState.first())
    }

    @Test
    fun `quote failure leaves the fee line empty but the entry usable`() = runTest {
        quoteReply = null
        val component = buildComponent()
        component.onReportDialogOpened()
        assertNull(component.proposalFeeQuote.first { true })
        component.onReportError("текст отчёта", null)
        assertEquals(ErrorReportSubmissionState.Done, component.errorReportState.first { it != ErrorReportSubmissionState.Idle })
    }

    @Test
    fun `report submission queues the proposal with trimmed texts and reports done`() = runTest {
        val component = buildComponent()
        component.onReportError("  текст отчёта  ", "  свой вариант  ")
        assertEquals(ErrorReportSubmissionState.Done, component.errorReportState.first())
        val proposal = recordedProposals.single()
        assertEquals("lesson-1", proposal.command.lessonId)
        assertEquals("текст отчёта", proposal.command.text)
        assertEquals("свой вариант", proposal.command.ownVariant)
        assertEquals("user-1", proposal.ownerUid)
        assertTrue(proposal.id.startsWith("lesson-proposal-"))
    }

    @Test
    fun `oversized report is refused before the queue and the state says failed`() = runTest {
        val component = buildComponent()
        component.onReportError("x".repeat(SubmitErrorReportProposalUseCase.MAX_REPORT_TEXT_CHARS + 1), null)
        assertEquals(ErrorReportSubmissionState.Failed, component.errorReportState.first())
        assertTrue(recordedProposals.isEmpty())
    }

    @Test
    fun `host without the report handles keeps the entry silent`() = runTest {
        val component = buildComponent(submit = null, quote = null)
        component.onReportDialogOpened()
        component.onReportError("текст", null)
        assertEquals(ErrorReportSubmissionState.Idle, component.errorReportState.first())
        assertTrue(recordedProposals.isEmpty())
    }
}
