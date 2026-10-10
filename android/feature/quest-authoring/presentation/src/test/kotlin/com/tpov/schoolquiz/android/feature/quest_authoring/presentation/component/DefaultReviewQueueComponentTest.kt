package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentSyncUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueFilter
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueKindUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewSegmentDecisionUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.UiMessage
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewActionSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignment
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentKind
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentSyncState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewChecks
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewQuestionScore
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewSegmentDecisionKind
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewAssignmentRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveReviewAssignmentSyncUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveReviewAssignmentsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitReviewActionUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultReviewQueueComponentTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val parser = KotlinxSerializationQuestionContentParser()
    private val json = Json { encodeDefaults = true }
    private lateinit var lifecycle: LifecycleRegistry

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `testing review requires score and submits selected score`() = runTest(testScheduler) {
        val repository = FakeReviewAssignmentRepository(
            initialAssignments = listOf(reviewAssignment(taskKinds = setOf(ReviewAssignmentKind.TESTING))),
        )
        val component = buildComponent(repository = repository)

        component.onAssignmentSelected("assignment-1")
        component.onSubmitClick()

        assertEquals(UiMessage.Res(R.string.qa_error_choose_score), component.state.value.errorMessage)
        assertTrue(repository.queued.isEmpty())

        component.onScoreSelected(2)
        component.onSubmitClick()

        // 4.6: оценки урока мало — TESTING судит каждый вопрос ревизии.
        assertEquals(UiMessage.Res(R.string.qa_error_score_all_questions), component.state.value.errorMessage)
        assertTrue(repository.queued.isEmpty())

        component.onQuestionScoreSelected("question-1", 2)
        component.onSubmitClick()

        val command = repository.queued.single().command
        assertEquals(ReviewAssignmentKind.TESTING, command.kind)
        assertEquals(2, command.score)
        assertEquals(listOf(ReviewQuestionScore("question-1", 2)), command.questionScores)
        assertNull(component.state.value.detail)
    }

    @Test
    fun `submit closes the form the same frame and only queues the intent`() = runTest(testScheduler) {
        val repository = FakeReviewAssignmentRepository(
            initialAssignments = listOf(reviewAssignment(taskKinds = setOf(ReviewAssignmentKind.TESTING))),
        )
        // Очередь ещё не ответила — как Room, ушедший на IO. Форма ждать её не обязана.
        repository.gate = CompletableDeferred()
        val component = buildComponent(repository = repository)
        component.onAssignmentSelected("assignment-1")
        component.onScoreSelected(2)
        component.onQuestionScoreSelected("question-1", 2)

        component.onSubmitClick()

        assertNull(component.state.value.detail, "форма закрывается тем же кадром")
        assertEquals(UiMessage.Res(R.string.qa_review_queued), component.state.value.successMessage)
        assertNull(component.state.value.errorMessage)
        assertTrue(repository.queued.isEmpty(), "запись ещё не легла — и это не держит форму")

        repository.gate?.complete(Unit)

        val submission = repository.queued.single()
        assertEquals("review-action-1", submission.id)
        assertEquals("reviewer-1", submission.ownerUid)
        assertEquals("assignment-1", submission.command.assignmentId)
        // Строка задания не менялась: список тот же, что и до нажатия (AD-25).
        assertEquals(listOf("assignment-1"), component.state.value.assignments.map { it.id })
    }

    @Test
    fun `queue failure is reported without reopening the form`() = runTest(testScheduler) {
        val repository = FakeReviewAssignmentRepository(
            initialAssignments = listOf(reviewAssignment(taskKinds = setOf(ReviewAssignmentKind.TESTING))),
            failure = IllegalStateException("disk full"),
        )
        val component = buildComponent(repository = repository)
        component.onAssignmentSelected("assignment-1")
        component.onScoreSelected(2)
        component.onQuestionScoreSelected("question-1", 2)

        component.onSubmitClick()

        assertNull(component.state.value.detail)
        assertNull(component.state.value.successMessage)
        assertEquals(UiMessage.Raw("disk full"), component.state.value.errorMessage)
    }

    @Test
    fun `card carries the queue mark for both outcomes`() = runTest(testScheduler) {
        val repository = FakeReviewAssignmentRepository(
            initialAssignments = listOf(reviewAssignment(taskKinds = setOf(ReviewAssignmentKind.TESTING))),
        )
        val component = buildComponent(repository = repository)

        assertEquals(ReviewAssignmentSyncUi.NONE, component.state.value.assignments.single().syncState)

        repository.syncStates.value = mapOf("assignment-1" to ReviewAssignmentSyncState.PENDING)
        assertEquals(ReviewAssignmentSyncUi.PENDING, component.state.value.assignments.single().syncState)

        // Задание переназначили или закрыли: сервер отказал окончательно, запись в карантине.
        // Откатывать нечего — карточка рисует пометку по ответу очереди (AD-25, AD-28).
        repository.syncStates.value = mapOf("assignment-1" to ReviewAssignmentSyncState.QUARANTINED)
        assertEquals(ReviewAssignmentSyncUi.QUARANTINED, component.state.value.assignments.single().syncState)

        // Фильтр перестраивает список, пометка при этом не теряется.
        component.onFilterSelected(ReviewQueueFilter.TESTING)
        assertEquals(ReviewAssignmentSyncUi.QUARANTINED, component.state.value.assignments.single().syncState)
    }

    @Test
    fun `translation sends question after all segments are filled`() = runTest(testScheduler) {
        val repository = FakeReviewAssignmentRepository(
            initialAssignments =
                listOf(
                    reviewAssignment(
                        taskKinds = setOf(ReviewAssignmentKind.TRANSLATION),
                        newTranslationLanguages = setOf("en"),
                    ),
                ),
        )
        val component = buildComponent(repository = repository)

        component.onAssignmentSelected("assignment-1")
        val segments = requireNotNull(component.state.value.detail).questions.single().segments
        assertTrue(segments.all { it.translatedText.isBlank() })

        component.onSubmitClick()

        assertEquals(UiMessage.Res(R.string.qa_error_fill_all_translations), component.state.value.errorMessage)
        assertTrue(repository.queued.isEmpty())

        segments.forEach { segment ->
            component.onTranslationTextChanged(
                questionId = segment.questionId,
                segmentKey = segment.key,
                value = "en-${segment.key}",
            )
        }
        component.onSubmitClick()

        val command = repository.queued.single().command
        assertEquals(ReviewAssignmentKind.TRANSLATION, command.kind)
        assertEquals("en", command.language)
        val translatedQuestion = command.translatedQuestions.single()
        assertEquals("en", translatedQuestion.language)
        assertEquals("en-text", translatedQuestion.text)
        val translatedContent = parser.parse(translatedQuestion.payload).getOrThrow()
        val singleChoice = translatedContent as QuestionContent.SingleChoice
        assertEquals("en-text", singleChoice.text)
        assertEquals("en-option:A", singleChoice.options[0].text)
        assertEquals("en-option:B", singleChoice.options[1].text)
        assertEquals("en-info", singleChoice.info)
    }

    @Test
    fun `translation review has its own filter and submits segment decisions`() = runTest(testScheduler) {
        val repository = FakeReviewAssignmentRepository(
            initialAssignments =
                listOf(
                    reviewAssignment(
                        taskKinds = setOf(ReviewAssignmentKind.TRANSLATION, ReviewAssignmentKind.TRANSLATION_REVIEW),
                        newTranslationLanguages = setOf("pl"),
                        reviewLanguages = setOf("en"),
                        questions = listOf(reviewQuestion(), translatedReviewQuestion(language = "en")),
                    ),
                ),
        )
        val component = buildComponent(repository = repository)

        assertEquals(
            listOf(
                ReviewQueueFilter.ALL,
                ReviewQueueFilter.TRANSLATION,
                ReviewQueueFilter.TRANSLATION_REVIEW,
            ),
            component.state.value.availableFilters,
        )

        component.onFilterSelected(ReviewQueueFilter.TRANSLATION_REVIEW)
        component.onAssignmentSelected("assignment-1")

        val detail = requireNotNull(component.state.value.detail)
        assertEquals(ReviewQueueKindUi.TRANSLATION_REVIEW, detail.kind)
        val segments = detail.questions.single().segments
        val firstSegment = segments.first()
        // Сегменты начинаются нерешёнными: молчаливого одобрения больше нет (CAP-3).
        assertTrue(segments.all { it.decision == null })

        component.onSubmitClick()
        assertEquals(UiMessage.Res(R.string.qa_error_decide_all_segments), component.state.value.errorMessage)
        assertTrue(repository.queued.isEmpty())

        component.onSegmentDecided(firstSegment.questionId, firstSegment.key, ReviewSegmentDecisionUi.ERROR)
        segments.drop(1).forEach { segment ->
            component.onSegmentDecided(segment.questionId, segment.key, ReviewSegmentDecisionUi.CORRECT)
        }
        component.onSubmitClick()

        val command = repository.queued.single().command
        assertEquals(ReviewAssignmentKind.TRANSLATION_REVIEW, command.kind)
        assertEquals("en", command.language)
        assertEquals(ReviewSegmentDecisionKind.ERROR, command.segmentResults.first { it.segmentKey == firstSegment.key }.decision)
        assertTrue(command.segmentResults.drop(1).all { it.decision == ReviewSegmentDecisionKind.CORRECT })
    }

    @Test
    fun `translation review starts undecided even when the target text is missing`() = runTest(testScheduler) {
        val repository = FakeReviewAssignmentRepository(
            initialAssignments =
                listOf(
                    reviewAssignment(
                        taskKinds = setOf(ReviewAssignmentKind.TRANSLATION_REVIEW),
                        reviewLanguages = setOf("en"),
                        questions = listOf(reviewQuestion()),
                    ),
                ),
        )
        val component = buildComponent(repository = repository)

        component.onAssignmentSelected("assignment-1")

        val segments = requireNotNull(component.state.value.detail).questions.single().segments
        assertTrue(segments.all { it.translatedText.isBlank() })
        // Отсутствующий перевод подсвечивается, но решение остаётся за ревьюером: никакой
        // автоотметки — ни одобрения, ни отказа (CAP-3).
        assertTrue(segments.all { it.decision == null })
    }

    private fun buildComponent(
        repository: FakeReviewAssignmentRepository = FakeReviewAssignmentRepository(),
        authRepository: FakeAuthRepository = FakeAuthRepository(),
    ): DefaultReviewQueueComponent {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return DefaultReviewQueueComponent(
            componentContext = DefaultComponentContext(lifecycle),
            authRepository = authRepository,
            observeReviewAssignments = ObserveReviewAssignmentsUseCase(repository),
            observeReviewAssignmentSync = ObserveReviewAssignmentSyncUseCase(repository),
            submitReviewAction =
                SubmitReviewActionUseCase(
                    repository = repository,
                    idProvider = { prefix -> "$prefix-1" },
                    timestampProvider = { 5L },
                ),
            questionContentParser = parser,
            mainContext = testDispatcher,
        )
    }

    private fun reviewAssignment(
        taskKinds: Set<ReviewAssignmentKind>,
        newTranslationLanguages: Set<String> = emptySet(),
        reviewLanguages: Set<String> = emptySet(),
        questions: List<ReviewQuestion> = listOf(reviewQuestion()),
    ): ReviewAssignment =
        ReviewAssignment(
            id = "assignment-1",
            submissionId = "submission-1",
            ownerUid = "reviewer-1",
            catalogId = "catalog-1",
            draftId = "draft-1",
            questId = "quest-1",
            lessonId = "lesson-1",
            title = "Lesson for review",
            createdAtMs = 1L,
            taskKinds = taskKinds,
            sourceLanguages = setOf("uk"),
            newTranslationLanguages = newTranslationLanguages,
            reviewLanguages = reviewLanguages,
            checks = ReviewChecks(),
            questions = questions,
        )

    private fun reviewQuestion(
        id: String = "question-1",
        language: String = "uk",
        content: QuestionContent.SingleChoice = singleChoiceContent(id = id),
    ): ReviewQuestion =
        ReviewQuestion(
            id = id,
            draftId = "draft-1",
            lessonId = "lesson-1",
            type = "single-choice",
            language = language,
            languageLevel = 10,
            difficulty = Difficulty.EASY.name,
            order = 0,
            text = content.text,
            imagePath = null,
            payload = json.encodeToString<QuestionContent>(content),
            updatedAtMs = 2L,
        )

    private fun translatedReviewQuestion(language: String): ReviewQuestion =
        reviewQuestion(
            id = "question-1__$language",
            language = language,
            content =
                singleChoiceContent(
                    id = "question-1__$language",
                    text = "Translated text",
                    optionA = "Translated A",
                    optionB = "Translated B",
                    info = "Translated info",
                ),
        )

    private fun singleChoiceContent(
        id: String,
        text: String = "Скільки буде 2 + 2?",
        optionA: String = "Три",
        optionB: String = "Чотири",
        info: String = "Пояснення",
    ): QuestionContent.SingleChoice =
        QuestionContent.SingleChoice(
            id = id,
            difficulty = Difficulty.EASY,
            text = text,
            imageUrl = null,
            options =
                listOf(
                    QuestionContent.Option(OptionId("A"), optionA),
                    QuestionContent.Option(OptionId("B"), optionB),
                ),
            correctOptionId = OptionId("B"),
            info = info,
        )

    private class FakeAuthRepository(
        initialUid: String? = "reviewer-1",
    ) : AuthRepository {
        private val uid = MutableStateFlow(initialUid)

        override suspend fun currentUid(): String? = uid.value

        override fun observeUid(): Flow<String?> = uid
    }

    private class FakeReviewAssignmentRepository(
        initialAssignments: List<ReviewAssignment> = emptyList(),
        private val failure: Throwable? = null,
    ) : ReviewAssignmentRepository {
        private val assignments = MutableStateFlow(initialAssignments)
        val queued = mutableListOf<ReviewActionSubmission>()
        val syncStates = MutableStateFlow<Map<String, ReviewAssignmentSyncState>>(emptyMap())

        /** Пока не завершён, постановка в очередь висит — как запись на диск. */
        var gate: CompletableDeferred<Unit>? = null

        override fun observeAssignments(ownerUid: String): Flow<List<ReviewAssignment>> = assignments

        override suspend fun queueReviewAction(submission: ReviewActionSubmission): Result<Unit> {
            gate?.await()
            failure?.let { return Result.failure(it) }
            queued += submission
            return Result.success(Unit)
        }

        override fun observeAssignmentSync(
            ownerUid: String,
            assignmentId: String,
        ): Flow<ReviewAssignmentSyncState> =
            syncStates.map { it[assignmentId] ?: ReviewAssignmentSyncState.NONE }
    }
}
