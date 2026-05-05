package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueFilter
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueKindUi
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignment
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentKind
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewChecks
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitReviewActionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewAssignmentRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveReviewAssignmentsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitReviewActionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertFalse
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

        assertEquals("Выберите оценку 1..3", component.state.value.errorMessage)
        assertTrue(repository.submittedCommands.isEmpty())

        component.onScoreSelected(2)
        component.onSubmitClick()

        val command = repository.submittedCommands.single()
        assertEquals(ReviewAssignmentKind.TESTING, command.kind)
        assertEquals(2, command.score)
        assertNull(component.state.value.detail)
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

        assertEquals("Заполните все поля перевода", component.state.value.errorMessage)
        assertTrue(repository.submittedCommands.isEmpty())

        segments.forEach { segment ->
            component.onTranslationTextChanged(
                questionId = segment.questionId,
                segmentKey = segment.key,
                value = "en-${segment.key}",
            )
        }
        component.onSubmitClick()

        val command = repository.submittedCommands.single()
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
        val firstSegment = detail.questions.single().segments.first()
        assertTrue(detail.questions.single().segments.all { it.accepted })

        component.onSegmentAcceptedChanged(firstSegment.questionId, firstSegment.key, accepted = false)
        component.onSubmitClick()

        val command = repository.submittedCommands.single()
        assertEquals(ReviewAssignmentKind.TRANSLATION_REVIEW, command.kind)
        assertEquals("en", command.language)
        assertFalse(command.segmentResults.first { it.segmentKey == firstSegment.key }.accepted)
    }

    @Test
    fun `translation review marks missing target text as rejected by default`() = runTest(testScheduler) {
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
        assertTrue(segments.all { !it.accepted })
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
            submitReviewAction = SubmitReviewActionUseCase(repository),
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
    ) : ReviewAssignmentRepository {
        private val assignments = MutableStateFlow(initialAssignments)
        val submittedCommands = mutableListOf<SubmitReviewActionCommand>()

        override fun observeAssignments(ownerUid: String): Flow<List<ReviewAssignment>> = assignments

        override suspend fun submitReviewAction(command: SubmitReviewActionCommand): Result<Unit> {
            submittedCommands += command
            return Result.success(Unit)
        }
    }
}
