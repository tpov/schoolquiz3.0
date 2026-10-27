package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsMarkStateUi
import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionCursor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionFilter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMark
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionVerdict
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionsAccessRefusedException
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewDecisionsRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.FetchReviewDecisionsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.MarkReviewDecisionUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * «Решения» (4.17, UX-22): экран читает страницу вердиктов, показывает детали с предпросмотром,
 * ставит ✓/✗ с комментарием («Отправляется…» → «Сохранено»), а отказ роли переводит вкладку на
 * авторскую сторону. Настоящий парсер схемы; хранилище — управляемая заглушка.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDecisionsComponentTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val parser = KotlinxSerializationQuestionContentParser()
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

    private fun decision(
        questionId: String = "q1",
        verdict: ReviewDecisionVerdict? = ReviewDecisionVerdict.FAIL,
        mark: ReviewDecisionMark? = null,
        payload: String = SINGLE_CHOICE_PAYLOAD,
    ) = ReviewDecision(
        unreadable = false,
        verdictId = "sub_l_WORDS_1",
        submissionId = "sub",
        questTitle = "Квест",
        lessonId = "l",
        lessonTitle = "Урок",
        stage = "WORDS",
        attempt = 1,
        revision = 3,
        model = "glm-test",
        promptVersion = "words-v1",
        sources = listOf("https://example.org/a"),
        rationale = "основание",
        questionId = questionId,
        verdict = verdict,
        reason = "запрещённое слово",
        question = ReviewDecisionQuestion("SingleChoice", "ru", "Вопрос", payload),
        mark = mark,
    )

    private fun component(repository: ReviewDecisionsRepository): DefaultDecisionsComponent {
        lifecycle = LifecycleRegistry().apply { resume() }
        return DefaultDecisionsComponent(
            componentContext = DefaultComponentContext(lifecycle),
            fetchDecisions = FetchReviewDecisionsUseCase(repository),
            markDecision = MarkReviewDecisionUseCase(repository),
            questionContentParser = parser,
            mainContext = testDispatcher,
        )
    }

    @Test
    fun `loads the page and shows the detail with a parsed preview and answer key`() = runTest(testScheduler) {
        val repository = FakeDecisionsRepository(pages = listOf(ReviewDecisionPage(listOf(decision()), hasMore = false, nextCursor = null)))
        val component = component(repository)
        val state = component.state.value
        assertFalse(state.isLoading)
        assertEquals(1, state.entries.size)
        assertEquals("Квест", state.entries.first().questTitle)

        component.onEntrySelected("sub_l_WORDS_1", "q1")
        val detail = component.state.value.detail!!
        assertEquals("glm-test", detail.model)
        assertEquals("words-v1", detail.promptVersion)
        assertEquals(1, detail.attempt)
        assertEquals(3L, detail.revision)
        assertEquals("основание", detail.rationale)
        assertFalse(detail.preview!!.parseFailed)
        assertEquals("Вопрос", detail.preview!!.text)
        assertEquals(listOf(true, false), detail.preview!!.options.map { it.correct })
        assertEquals("верный", detail.preview!!.answerKeyLine)
    }

    @Test
    fun `marking saves through and updates the row`() = runTest(testScheduler) {
        val repository = FakeDecisionsRepository(
            pages = listOf(ReviewDecisionPage(listOf(decision()), hasMore = false, nextCursor = null)),
            markResult = Result.success(ReviewDecisionMark(ReviewDecisionMarkValue.VALID, "пересмотрел", "admin", 42)),
        )
        val component = component(repository)
        component.onEntrySelected("sub_l_WORDS_1", "q1")
        component.onCommentChanged("пересмотрел")
        component.onMarkSelected(ReviewDecisionMarkValue.VALID)
        testScheduler.runCurrent()

        val detail = component.state.value.detail!!
        assertEquals(ReviewDecisionMarkValue.VALID, detail.markValue)
        assertEquals(DecisionsMarkStateUi.SAVED, detail.markState)
        assertEquals("пересмотрел", detail.markComment)
    }

    @Test
    fun `a failed mark returns the control to none and names the error`() = runTest(testScheduler) {
        val repository = FakeDecisionsRepository(
            pages = listOf(ReviewDecisionPage(listOf(decision()), hasMore = false, nextCursor = null)),
            markResult = Result.failure(IllegalStateException("boom")),
        )
        val component = component(repository)
        component.onEntrySelected("sub_l_WORDS_1", "q1")
        component.onMarkSelected(ReviewDecisionMarkValue.INVALID)
        testScheduler.runCurrent()

        val state = component.state.value
        assertEquals(DecisionsMarkStateUi.NONE, state.detail!!.markState)
        assertNull(state.detail!!.markValue)
        assertTrue(state.errorMessage != null)
    }

    @Test
    fun `access refused flips the tab to the proposals-only side`() = runTest(testScheduler) {
        val repository = FakeDecisionsRepository(refused = true)
        val component = component(repository)
        val state = component.state.value
        assertFalse(state.isLoading)
        assertTrue(state.accessRefused)
        assertTrue(state.entries.isEmpty())
    }

    @Test
    fun `a broken payload degrades the preview instead of guessing a question`() = runTest(testScheduler) {
        val repository = FakeDecisionsRepository(
            pages = listOf(ReviewDecisionPage(listOf(decision(payload = "not-json")), hasMore = false, nextCursor = null)),
        )
        val component = component(repository)
        component.onEntrySelected("sub_l_WORDS_1", "q1")
        val preview = component.state.value.detail!!.preview!!
        assertTrue(preview.parseFailed)
        assertEquals("Вопрос", preview.text)
    }

    @Test
    fun `filter selection reloads from scratch with the chosen filter`() = runTest(testScheduler) {
        val repository = FakeDecisionsRepository(
            pages = listOf(
                ReviewDecisionPage(listOf(decision()), hasMore = false, nextCursor = null),
                ReviewDecisionPage(listOf(decision(mark = ReviewDecisionMark(ReviewDecisionMarkValue.INVALID, "", "admin", 1))), hasMore = false, nextCursor = null),
            ),
        )
        val component = component(repository)
        component.onFilterSelected(ReviewDecisionFilter.VALID)
        assertEquals(ReviewDecisionFilter.VALID, component.state.value.selectedFilter)
        // Первая загрузка — UNMARKED по умолчанию, смена фильтра перечитывает с начала.
        assertEquals(listOf(ReviewDecisionFilter.UNMARKED, ReviewDecisionFilter.VALID), repository.fetchedFilters)
    }

    private companion object {
        val SINGLE_CHOICE_PAYLOAD =
            """{"type":"SingleChoice","id":"q1","difficulty":"EASY","text":"Вопрос","imageUrl":null,""" +
                """"options":[{"id":"a","text":"верный"},{"id":"b","text":"другое"}],"correctOptionId":"a"}"""
    }
}

private class FakeDecisionsRepository(
    private val pages: List<ReviewDecisionPage> = emptyList(),
    private val markResult: Result<ReviewDecisionMark>? = null,
    private val refused: Boolean = false,
) : ReviewDecisionsRepository {
    val fetchedFilters = mutableListOf<ReviewDecisionFilter>()
    private var page = 0

    override suspend fun fetch(filter: ReviewDecisionFilter, cursor: ReviewDecisionCursor?): Result<ReviewDecisionPage> {
        if (refused) throw ReviewDecisionsAccessRefusedException()
        fetchedFilters += filter
        val result = pages[page.coerceAtMost(pages.lastIndex)]
        page += 1
        return Result.success(result)
    }

    override suspend fun mark(
        verdictId: String,
        questionId: String,
        mark: ReviewDecisionMarkValue,
        comment: String,
    ): Result<ReviewDecisionMark> = markResult ?: error("mark not prepared")
}
