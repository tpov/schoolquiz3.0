package com.tpov.schoolquiz.android.feature.internet.social.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollMode
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollOption
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollPublishCommand
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollPublishSubmission
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestion
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesDraft
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesSummary
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollVoteCommand
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollVoteSubmission
import com.tpov.schoolquiz.shared.feature.internet.social.domain.provider.PollsIdProvider
import com.tpov.schoolquiz.shared.feature.internet.social.domain.provider.PollsTimestampProvider
import com.tpov.schoolquiz.shared.feature.internet.social.domain.repository.PollsRepository
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.GetPollQuestionStateUseCase
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.ListPollSeriesUseCase
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.PublishPollSeriesUseCase
import com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case.SubmitPollVoteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Поверхность опросов на экране (UX-O7, история 8.2): подтверждённый набор уезжает очередью,
 * распределение появляется только из серверной вью — «ждёт подтверждения» честно живёт до
 * собственного принятого голоса; до отправки набор правится свободно, SINGLE заменяет выбор.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPollsComponentTest {
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
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
    fun `series list loads from the view and opens the first question`() = runTest(testScheduler) {
        val fakes = FakeRepository()
        val component = buildComponent(fakes)
        val collected = launchCollector(component)

        assertEquals(listOf("poll-1"), component.state.value.series.map { it.seriesId })
        assertNull(component.state.value.errorMessage)

        component.onOpenSeriesClick("poll-1")
        assertEquals("poll-1", component.state.value.openSeriesId)
        assertEquals("Favorite color?", component.state.value.questionState?.question?.text)
        assertNull(component.state.value.questionState?.distribution, "до голоса распределение скрыто")
        collected.cancel()
    }

    @Test
    fun `single choice replaces the selection, multiple toggles`() = runTest(testScheduler) {
        val fakes = FakeRepository()
        val component = buildComponent(fakes)
        launchCollector(component)
        component.onOpenSeriesClick("poll-1") // q0 SINGLE

        component.onToggleOptionClick("opt-1")
        component.onToggleOptionClick("opt-2")
        assertEquals(setOf("opt-2"), component.state.value.selectedOptionIds, "SINGLE — один вариант")

        component.onNextQuestionClick() // q1 MULTIPLE
        component.onToggleOptionClick("opt-1")
        component.onToggleOptionClick("opt-2")
        component.onToggleOptionClick("opt-2")
        assertEquals(setOf("opt-1"), component.state.value.selectedOptionIds, "MULTIPLE — тап снимает отметку")
    }

    @Test
    fun `confirmed vote queues and the distribution arrives only from the view`() = runTest(testScheduler) {
        val fakes = FakeRepository()
        val component = buildComponent(fakes)
        launchCollector(component)
        component.onOpenSeriesClick("poll-1")

        component.onToggleOptionClick("opt-1")
        component.onConfirmVoteClick()

        assertEquals(true, component.state.value.voteQueued, "голос в очереди, подтверждения ещё нет")
        assertEquals(1, fakes.votes.size, "ровно одно намерение")
        val command = fakes.votes.single().command
        assertEquals(listOf("opt-1"), command.selectedOptionIds)
        assertEquals(1L, command.revisionId)

        // Дренаж произошёл, вью отдала собственный принятый голос: «ждёт» снимается,
        // распределение появляется — и это факт сервера, не локальная догадка.
        fakes.answerVote(Pair("poll-1", 0))
        testScheduler.advanceTimeBy(1_500)

        assertEquals(false, component.state.value.voteQueued)
        assertEquals(listOf("opt-1"), component.state.value.questionState?.ownVote?.selectedOptionIds)
        assertEquals(1, component.state.value.questionState?.distribution?.respondentCount)
    }

    @Test
    fun `authoring builds the draft and publishes one series`() = runTest(testScheduler) {
        val fakes = FakeRepository()
        val component = buildComponent(fakes)
        launchCollector(component)

        component.onCreateSeriesClick()
        component.onAuthorTitleChange("Journey series")
        component.onAuthorQuestionTextChange(0, "Pick one")
        component.onAuthorModeChange(0, PollMode.SINGLE)
        component.onAuthorOptionTextChange(0, 0, "Red")
        component.onAuthorOptionTextChange(0, 1, "Green")
        component.onAuthorAddOptionClick(0)
        component.onAuthorOptionTextChange(0, 2, "Blue")

        component.onPublishClick()
        assertEquals(1, fakes.publishes.size, "ровно одно намерение публикации")
        val command = fakes.publishes.single().command
        assertEquals("Journey series", command.title)
        assertEquals(0L, command.expectedRevisionId)
        assertEquals(3, command.questions.single().options.size)

        fakes.addSeries(PollSeriesSummary(command.seriesId, "Journey series", 1, 1, 0, own = true))
        testScheduler.advanceTimeBy(1_500)
        assertTrue(
            component.state.value.series.any { it.title == "Journey series" },
            "серия появляется из вью после дренажа",
        )
        assertNull(component.state.value.authoring, "форма закрывается подтверждением сервера")
        assertFalse(component.state.value.publishQueued)
    }

    // ── fakes ──

    private class FakeAuthRepository : AuthRepository {
        val uid = MutableStateFlow<String?>("participant-1")
        override suspend fun currentUid(): String? = uid.value
        override fun observeUid(): Flow<String?> = uid
    }

    private class FakePollsIdProvider : PollsIdProvider {
        override fun nextId(prefix: String): String = "$prefix-fixed"
    }

    private class FakePollsTimestampProvider : PollsTimestampProvider {
        override fun nowMs(): Long = 1_760_000_000_000
    }

    private class FakeRepository : PollsRepository {
        val votes = mutableListOf<PollVoteSubmission>()
        val publishes = mutableListOf<PollPublishSubmission>()
        private val summaries =
            mutableListOf(
                PollSeriesSummary(
                    seriesId = "poll-1",
                    title = "Demo",
                    revisionId = 1,
                    questionCount = 2,
                    answeredCount = 0,
                    own = false,
                ),
            )
        private val answered = mutableSetOf<Pair<String, Int>>()

        fun addSeries(summary: PollSeriesSummary) {
            summaries += summary
        }

        fun answerVote(key: Pair<String, Int>) {
            answered += key
        }

        override suspend fun listSeries(pageSize: Int): Result<List<PollSeriesSummary>> =
            Result.success(summaries.toList())

        override suspend fun questionState(
            seriesId: String,
            questionIndex: Int,
        ): Result<PollQuestionState> {
            val own = (seriesId to questionIndex) in answered
            return Result.success(
                PollQuestionState(
                    seriesId = seriesId,
                    title = "Demo",
                    own = false,
                    revisionId = 1,
                    questionCount = 2,
                    questionIndex = questionIndex,
                    question =
                        PollQuestion(
                            questionIndex = questionIndex,
                            text = if (questionIndex == 0) "Favorite color?" else "Which fruits?",
                            mode = if (questionIndex == 0) PollMode.SINGLE else PollMode.MULTIPLE,
                            options =
                                listOf(
                                    PollOption("opt-1", "One"),
                                    PollOption("opt-2", "Two"),
                                ),
                        ),
                    ownVote = if (own) com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollOwnVote(listOf("opt-1"), 42L) else null,
                    distribution = if (own) com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollDistribution(mapOf("opt-1" to 1, "opt-2" to 0), 1) else null,
                ),
            )
        }

        override suspend fun queueVote(submission: PollVoteSubmission): Result<Unit> {
            votes += submission
            return Result.success(Unit)
        }

        override suspend fun queuePublish(submission: PollPublishSubmission): Result<Unit> {
            publishes += submission
            return Result.success(Unit)
        }
    }

    private fun buildComponent(fakes: FakeRepository): DefaultPollsComponent =
        DefaultPollsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry().also {
                it.resume()
            }.also { lifecycle = it }),
            authRepository = FakeAuthRepository(),
            listSeries = ListPollSeriesUseCase(fakes),
            questionState = GetPollQuestionStateUseCase(fakes),
            submitVote = SubmitPollVoteUseCase(fakes, FakePollsIdProvider(), FakePollsTimestampProvider()),
            publishSeries = PublishPollSeriesUseCase(fakes, FakePollsIdProvider(), FakePollsTimestampProvider()),
            idProvider = FakePollsIdProvider(),
            mainContext = testDispatcher,
        )

    private fun launchCollector(component: DefaultPollsComponent) =
        kotlinx.coroutines.CoroutineScope(testDispatcher).launch {
            component.state.collect { }
        }
}
