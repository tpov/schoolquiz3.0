package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeEconomyRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.LessonUnlockKind
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson.domain.logic.LessonAccess
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sequential lesson access in a course catalog.
 *
 * The gate exists so a theme test cannot be bought open: nolics grant access to a lesson but earn
 * no stars, so a bought-and-unplayed lesson stops the chain exactly where it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonSequentialGateTest {

    private val testScheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry
    private val fakeLessonRepo = FakeLessonRepository()
    private val fakeAttemptRepo = FakeLessonAttemptRepository()
    private val fakeAuthRepo = FakeAuthRepository(initialUid = "user1")
    private val fakeEconomyRepo = FakeEconomyRepository()
    private val fakeNavigation = FakeStackNavigation()

    /** Цены, как их посчитал бы ObserveLessonUnlockPricesUseCase из вопросов уроков. */
    private val prices = MutableStateFlow<Map<LessonId, Int>>(emptyMap())

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
    }

    private fun buildComponent(questType: QuestType): DefaultLessonListComponent {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return DefaultLessonListComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            config = QuizzesConfig.LessonList(
                themeId = "t-1",
                breadcrumbs = listOf(BreadcrumbRoot.Dynamic("Theme A")),
                questType = questType,
            ),
            lessonRepository = fakeLessonRepo,
            attemptRepository = fakeAttemptRepo,
            authRepository = fakeAuthRepo,
            economyRepository = fakeEconomyRepo,
            navigation = fakeNavigation,
            observeUnlockPrices = { prices },
            coroutineContext = dispatcher,
        )
    }

    private fun lesson(id: String, order: Int) = Lesson(
        id = LessonId(id),
        themeId = ThemeId("t-1"),
        title = "Урок $order",
        order = order,
        version = 1L,
        lastModifiedAt = 0L,
    )

    /** An all-nines EASY attempt is what "passed" means: every easy question answered correctly. */
    private fun passedAttempt(lessonId: String) = Attempt(
        id = AttemptId("a-$lessonId"),
        userId = "user1",
        lessonId = LessonId(lessonId),
        lessonVersion = 1L,
        mode = Difficulty.EASY,
        completedAt = 1_000L,
        codeAnswer = CodeAnswer("999"),
        percentScore = PercentScore(100),
    )

    @Test
    fun `a shut lesson says what it costs`() = runTest(dispatcher) {
        // Замок рисовался без числа: цену знал только сервер, и спросить её было нельзя, не купив.
        val component = buildComponent(QuestType.COURSE)
        prices.value = mapOf(LessonId("l2") to 53, LessonId("l3") to 106)
        fakeLessonRepo.emit(listOf(lesson("l1", 0), lesson("l2", 1), lesson("l3", 2)))
        fakeAttemptRepo.emit(emptyList())
        advanceUntilIdle()

        val shown = items(component).associate { it.id to it.unlockPriceNolics }
        assertEquals(53, shown["l2"])
        assertEquals(106, shown["l3"])
    }

    @Test
    fun `a lesson whose questions have not arrived shows no price at all`() = runTest(dispatcher) {
        // Ноль обещал бы бесплатную дверь, единица — дешёвую; обе разошлись бы со списанием.
        val component = buildComponent(QuestType.COURSE)
        prices.value = mapOf(LessonId("l2") to 53)
        fakeLessonRepo.emit(listOf(lesson("l1", 0), lesson("l2", 1), lesson("l3", 2)))
        fakeAttemptRepo.emit(emptyList())
        advanceUntilIdle()

        assertEquals(null, items(component).first { it.id == "l3" }.unlockPriceNolics)
    }

    @Test
    fun `a price that arrives after the list does still reaches the row`() = runTest(dispatcher) {
        // Вопросы урока доезжают синхронизацией позже самого урока — цена обязана догнать список,
        // а не остаться пустой до следующего открытия экрана.
        val component = buildComponent(QuestType.COURSE)
        fakeLessonRepo.emit(listOf(lesson("l1", 0), lesson("l2", 1)))
        fakeAttemptRepo.emit(emptyList())
        advanceUntilIdle()
        assertEquals(null, items(component).first { it.id == "l2" }.unlockPriceNolics)

        prices.value = mapOf(LessonId("l2") to 53)
        advanceUntilIdle()

        assertEquals(53, items(component).first { it.id == "l2" }.unlockPriceNolics)
    }

    private fun items(component: DefaultLessonListComponent) =
        (component.uiState.value as LessonListUiState.Loaded).items

    @Test
    fun `a course locks every lesson after the first until it is passed`() = runTest(dispatcher) {
        val component = buildComponent(QuestType.COURSE)
        fakeLessonRepo.emit(listOf(lesson("l1", 0), lesson("l2", 1), lesson("l3", 2)))
        fakeAttemptRepo.emit(emptyList())
        advanceUntilIdle()

        val access = items(component).associate { it.id to it.access }
        assertEquals(LessonAccess.OPEN, access["l1"])
        assertEquals(LessonAccess.LOCKED, access["l2"])
        assertEquals(LessonAccess.LOCKED, access["l3"])
    }

    @Test
    fun `passing the first lesson opens the second and no further`() = runTest(dispatcher) {
        val component = buildComponent(QuestType.COURSE)
        fakeLessonRepo.emit(listOf(lesson("l1", 0), lesson("l2", 1), lesson("l3", 2)))
        fakeAttemptRepo.emit(listOf(passedAttempt("l1")))
        advanceUntilIdle()

        val access = items(component).associate { it.id to it.access }
        assertEquals(LessonAccess.OPEN, access["l2"])
        assertEquals(LessonAccess.LOCKED, access["l3"])
    }

    @Test
    fun `a regular catalog gates nothing`() = runTest(dispatcher) {
        val component = buildComponent(QuestType.REGULAR)
        fakeLessonRepo.emit(listOf(lesson("l1", 0), lesson("l2", 1), lesson("l3", 2)))
        fakeAttemptRepo.emit(emptyList())
        advanceUntilIdle()

        assertTrue(items(component).all { it.access == LessonAccess.OPEN })
    }

    @Test
    fun `a bought lesson is open but still stops the chain`() = runTest(dispatcher) {
        val component = buildComponent(QuestType.COURSE)
        fakeEconomyRepo.emit(
            EconomyResourceBalance(lessonUnlocks = setOf(LessonUnlockKind.LESSON.keyFor("l2"))),
        )
        fakeLessonRepo.emit(listOf(lesson("l1", 0), lesson("l2", 1), lesson("l3", 2)))
        fakeAttemptRepo.emit(emptyList())
        advanceUntilIdle()

        val access = items(component).associate { it.id to it.access }
        // Bought, so it opens — but it was never played, so lesson 3 stays shut. This is what
        // keeps a whole theme from being bought open ahead of its test.
        assertEquals(LessonAccess.PURCHASED, access["l2"])
        assertEquals(LessonAccess.LOCKED, access["l3"])
    }

    @Test
    fun `a bought lesson opens both difficulties`() = runTest(dispatcher) {
        // It is priced as both, so it has to grant both — otherwise two thirds of the price buys
        // a gate the player still has to earn.
        val component = buildComponent(QuestType.COURSE)
        fakeEconomyRepo.emit(
            EconomyResourceBalance(lessonUnlocks = setOf(LessonUnlockKind.LESSON.keyFor("l2"))),
        )
        fakeLessonRepo.emit(listOf(lesson("l1", 0), lesson("l2", 1)))
        fakeAttemptRepo.emit(emptyList())
        advanceUntilIdle()

        val bought = items(component).first { it.id == "l2" }
        assertEquals(LessonAccess.PURCHASED, bought.access)
        assertTrue(bought.hardUnlocked, "a bought lesson opens hard mode as well as easy")
        // A lesson nobody bought or played keeps hard mode shut.
        assertTrue(!items(component).first { it.id == "l1" }.hardUnlocked)
    }

    @Test
    fun `tapping a locked lesson asks to buy it`() = runTest(dispatcher) {
        val component = buildComponent(QuestType.COURSE)
        fakeLessonRepo.emit(listOf(lesson("l1", 0), lesson("l2", 1)))
        fakeAttemptRepo.emit(emptyList())
        advanceUntilIdle()

        component.onLessonClick(items(component).first { it.id == "l2" })
        advanceUntilIdle()

        assertEquals(listOf("l2" to LessonUnlockKind.LESSON), fakeEconomyRepo.unlockCalls)
        // And once bought, it opens without a restart.
        assertEquals(LessonAccess.PURCHASED, items(component).first { it.id == "l2" }.access)
    }

    @Test
    fun `a refused purchase says why instead of leaving a dead button`() = runTest(dispatcher) {
        val component = buildComponent(QuestType.COURSE)
        fakeEconomyRepo.unlockFailure = IllegalStateException("Не хватает ноликов")
        fakeLessonRepo.emit(listOf(lesson("l1", 0), lesson("l2", 1)))
        fakeAttemptRepo.emit(emptyList())
        advanceUntilIdle()

        val messages = mutableListOf<String>()
        val collector = launch { messages += component.messages.first() }

        component.onLessonClick(items(component).first { it.id == "l2" })
        advanceUntilIdle()
        collector.join()

        assertEquals(listOf("Не хватает ноликов"), messages)
        // And the row stays shut, because nothing was bought.
        assertEquals(LessonAccess.LOCKED, items(component).first { it.id == "l2" }.access)
    }

    @Test
    fun `a second tap while the first purchase is in flight does not charge twice`() = runTest(dispatcher) {
        val component = buildComponent(QuestType.COURSE)
        val gate = CompletableDeferred<Unit>()
        fakeEconomyRepo.unlockGate = gate
        fakeLessonRepo.emit(listOf(lesson("l1", 0), lesson("l2", 1)))
        fakeAttemptRepo.emit(emptyList())
        advanceUntilIdle()

        val locked = items(component).first { it.id == "l2" }
        component.onLessonClick(locked)
        component.onLessonClick(locked)
        advanceUntilIdle()

        assertEquals(1, fakeEconomyRepo.unlockCalls.size)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `tapping a locked lesson does not open the runner`() = runTest(dispatcher) {
        val component = buildComponent(QuestType.COURSE)
        fakeLessonRepo.emit(listOf(lesson("l1", 0), lesson("l2", 1)))
        fakeAttemptRepo.emit(emptyList())
        advanceUntilIdle()

        val locked = items(component).first { it.id == "l2" }
        component.onLessonClick(locked)
        advanceUntilIdle()

        assertTrue(
            fakeNavigation.pushedConfigs.none { it is QuizzesConfig.LessonRunner },
            "a shut lesson must not reach the runner",
        )
    }
}
