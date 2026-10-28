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
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.CopyEditLoadState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.CopyEditSubmissionState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.logic.EditCopyMark
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.CopyEditProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ErrorReportProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopy
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopyRow
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonProposalRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.FetchLessonEditCopyUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.QuoteLessonProposalFeeUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitCopyEditProposalUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * «Предложить правку» (история 4.23): копия грузится строками без ключа, отметка ответа —
 * цитата 100×N, подача увозит только отмеченные строки в авторской форме. Хост без ручек —
 * вход молчит.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CopyEditProposalPresentationTest {
    private lateinit var lifecycle: LifecycleRegistry

    private val recordedCopyEdits = mutableListOf<CopyEditProposal>()
    private val quoteCalls = mutableListOf<Pair<String, Int>>()
    private var editCopyReply: LessonEditCopy? = null
    private var quoteReply: LessonProposalFeeQuote? =
        LessonProposalFeeQuote(kind = "COPY_EDIT", feeNolics = 100, tariffRevision = 1)

    private val fakeRepository =
        object : LessonProposalRepository {
            override suspend fun queueErrorReportProposal(proposal: ErrorReportProposal): Result<Unit> =
                Result.success(Unit)

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
                quoteCalls += kind to changedQuestions
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
        fetch: FetchLessonEditCopyUseCase? = FetchLessonEditCopyUseCase(fakeRepository),
        submit: SubmitCopyEditProposalUseCase? = SubmitCopyEditProposalUseCase(
            repository = fakeRepository,
            idProvider = { prefix -> "$prefix-test-1" },
            timestampProvider = { 42L },
        ),
        quote: QuoteLessonProposalFeeUseCase? = QuoteLessonProposalFeeUseCase(fakeRepository),
        currentUid: () -> String? = { "user-1" },
    ): DefaultLessonRunnerRootComponent {
        val clock = FakeClock()
        val lessonRepo = FakeLessonRepository()
        return DefaultLessonRunnerRootComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            lessonId = LessonId("lesson-1"),
            mode = Difficulty.EASY,
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
            fetchLessonEditCopy = fetch,
            submitCopyEditProposal = submit,
            quoteProposalFee = quote,
            currentUid = currentUid,
            mainContext = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `dialog open loads the copy rows and quotes the fee for zero edits`() = runTest {
        editCopyReply = singleRowCopy()
        val component = buildComponent()

        component.onCopyEditDialogOpened()

        val state = component.copyEditSheetState.value
        assertEquals(CopyEditLoadState.Loaded, state.load)
        assertEquals(1, state.rows.size)
        assertTrue(state.rows.first().row.editable)
        assertTrue(state.rows.first().parsed is com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent.SingleChoice)
        assertEquals(listOf("COPY_EDIT" to 0), quoteCalls)
    }

    @Test
    fun `marking a row requotes the fee for the changed count`() = runTest {
        editCopyReply = singleRowCopy()
        val component = buildComponent()
        component.onCopyEditDialogOpened()

        component.onCopyEditMarkChanged("q-1", EditCopyMark.SingleChoice("ri-0"))

        assertEquals(listOf("COPY_EDIT" to 0, "COPY_EDIT" to 1), quoteCalls)
        assertTrue(component.copyEditSheetState.value.editedRows.any { it.row.id == "q-1" })
    }

    @Test
    fun `submit queues only the marked row in authoring form and reports done`() = runTest {
        editCopyReply = singleRowCopy()
        val component = buildComponent()
        component.onCopyEditDialogOpened()
        component.onCopyEditMarkChanged("q-1", EditCopyMark.SingleChoice("ri-1"))

        component.onCopyEditSubmit()

        assertEquals(1, recordedCopyEdits.size)
        val proposal = recordedCopyEdits.first()
        assertEquals("lesson-1", proposal.command.lessonId)
        assertEquals(listOf("q-1"), proposal.command.editedQuestionIds)
        val question = proposal.command.questions.single()
        assertEquals("q-1", question.id)
        // Авторская форма: публичная половина + отметка = полный payload с ключом.
        assertTrue(question.payload.contains("\"SingleChoice\""), question.payload)
        assertTrue(question.payload.contains("ri-1"), question.payload)
        assertEquals(
            CopyEditSubmissionState.Done,
            component.copyEditSheetState.value.submission,
        )
    }

    @Test
    fun `submit without marks is a no-op`() = runTest {
        editCopyReply = singleRowCopy()
        val component = buildComponent()
        component.onCopyEditDialogOpened()

        component.onCopyEditSubmit()

        assertTrue(recordedCopyEdits.isEmpty())
        assertEquals(CopyEditSubmissionState.Idle, component.copyEditSheetState.value.submission)
    }

    @Test
    fun `host without the copy handles keeps the entry silent`() = runTest {
        val component = buildComponent(fetch = null, submit = null)

        component.onCopyEditDialogOpened()

        assertEquals(CopyEditLoadState.Idle, component.copyEditSheetState.value.load)
        assertTrue(recordedCopyEdits.isEmpty())
    }

    private fun singleRowCopy() = LessonEditCopy(
        lessonId = "lesson-1",
        lessonTitle = "Чужой урок",
        baseRevision = 4,
        questions =
            listOf(
                LessonEditCopyRow(
                    id = "q-1",
                    lessonId = "lesson-1",
                    type = "SingleChoice",
                    language = "ru",
                    languageLevel = 0,
                    difficulty = "EASY",
                    order = 0,
                    text = "Сколько будет два на два?",
                    imagePath = null,
                    payload =
                        "{\"type\":\"SingleChoiceRedacted\",\"difficulty\":\"EASY\"," +
                            "\"text\":\"Сколько будет два на два?\",\"imageUrl\":null," +
                            "\"options\":[{\"id\":\"ri-0\",\"text\":\"Четыре\"},{\"id\":\"ri-1\",\"text\":\"Пять\"}]}",
                    editable = true,
                ),
            ),
    )
}
