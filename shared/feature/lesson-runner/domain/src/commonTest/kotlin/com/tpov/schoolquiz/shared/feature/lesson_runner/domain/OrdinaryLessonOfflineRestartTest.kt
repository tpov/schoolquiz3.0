package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * История 2.1 (ordinary-lesson-offline-restart), переходы RunnerLogic.
 *
 * Обычный урок целиком идёт офлайн из подготовленного запаса: старт берёт вопросы только из
 * локального кэша, ответы прогоняют RunnerLogic до сентинела, завершение сохраняет попытку —
 * и после «рестарта» (новые потребители поверх того же хранилища) показывается та же попытка
 * и тот же исход (CAP-1). Сетевой-гейт-отрицание: единственные сетевые по форме методы
 * репозиториев ([QuestionRepository.refreshByParents], [LessonRepository.refreshByParents])
 * за весь путь не вызываются ни разу.
 */
class OrdinaryLessonOfflineRestartTest {

    private val clock = FakeClock()
    private val authRepo = FakeAuthRepository(uid = "user1")
    private val attemptRepo = FakeLessonAttemptRepository()
    private val parser = FakeQuestionContentParser()

    private val questions = PreparedStockQuestions(
        listOf(
            stockQuestion("q1", order = 0, payload = "p1"),
            stockQuestion("q2", order = 1, payload = "p2"),
            stockQuestion("q3", order = 2, payload = "p3"),
        ),
    )

    private val lessons = LocalLessons(mapOf("lesson1" to makeLesson(id = "lesson1")))

    private var idCounter = 0

    private val startUseCase get() = StartLessonAttemptUseCase(
        questionRepository = questions,
        lessonRepository = lessons,
        parser = parser,
        authRepository = authRepo,
        clock = clock,
        randomSeedProvider = { 12345L },
        attemptIdProvider = { AttemptId("attempt-${idCounter++}") },
    )

    private val completeUseCase = CompleteAttemptUseCase(
        attemptRepository = attemptRepo,
        clock = clock,
    )

    init {
        parser.addResponse("p1", singleChoiceContent(correctId = "A"))
        parser.addResponse("p2", singleChoiceContent(correctId = "A"))
        parser.addResponse("p3", singleChoiceContent(correctId = "A"))
    }

    /** Готовый сохранённый вопрос появляется без сетевого гейта: старт читает только запас. */
    @Test
    fun startServesThePreparedStockWithoutAnyNetworkCall() = runTest {
        val ready = assertIs<RunnerState.Ready>(
            startUseCase(LessonId("lesson1"), Difficulty.EASY),
        )

        assertEquals(setOf("q1", "q2", "q3"), ready.playOrder.map { it.sourceId.value }.toSet())
        assertEquals(3, ready.eligibleSize)
        assertEquals(0, questions.refreshCalls, "CAP-1: старт не ходит в сеть за вопросами")
        assertEquals(0, lessons.refreshCalls, "CAP-1: старт не ходит в сеть за уроком")
    }

    /** Ответы идут до сентинела, попытка одна и та же на каждом переходе, исход — 100%. */
    @Test
    fun offlineAnswersRunToSentinelAndCompletionKeepsTheSameAttempt() = runTest {
        var state = assertIs<RunnerState.Ready>(
            startUseCase(LessonId("lesson1"), Difficulty.EASY),
        )
        val attemptId = state.attemptId
        var nowMs = 1_000_000L

        while (state.indexInPool < state.playOrder.size) {
            state = submitAnswer(state, UserAnswer.SingleChoiceAnswer(optId("A")), nowMs)
            nowMs += 1_000
            assertEquals(attemptId, state.attemptId, "идентичность попытки переживает переход")
        }

        assertEquals(state.playOrder.size, state.indexInPool, "последний ответ даёт сентинел")
        assertEquals("999", requireNotNull(state.codeAnswer).raw, "три верных ответа — три девятки")

        val completed = assertIs<RunnerState.Completed>(completeUseCase(state))
        assertEquals(attemptId, completed.attempt.id)
        assertEquals(100, completed.attempt.percentScore.raw)
        assertEquals(1, attemptRepo.saveCallCount, "завершение сохраняет попытку ровно один раз")
    }

    /**
     * «Рестарт»: новые потребители читают то же хранилище — и видят ту же попытку/исход;
     * новый запуск того же урока снова стартует офлайн из запаса.
     */
    @Test
    fun afterRestartTheSameAttemptOutcomeIsShownAndStartWorksOfflineAgain() = runTest {
        val first = startOfflineAndComplete()

        // После рестарта экран читает тот же источник: показан ровно сохранённый исход.
        val shown = attemptRepo.observeByLesson("user1", LessonId("lesson1")).first()
        assertEquals(listOf(first.attempt), shown)

        val second = assertIs<RunnerState.Ready>(
            startUseCase(LessonId("lesson1"), Difficulty.EASY),
        )
        assertEquals(0, questions.refreshCalls, "и повторный старт остаётся офлайн (CAP-1)")
        assertEquals(0, lessons.refreshCalls)
        assertNotEquals(first.attempt.id, second.attemptId, "новый запуск — новая попытка")
        assertEquals(1, attemptRepo.saveCallCount, "старт не пишет попыток")
    }

    /** Полный офлайн-прогон: старт из запаса → ответы до сентинела → завершение. */
    private suspend fun startOfflineAndComplete(): RunnerState.Completed {
        var state = assertIs<RunnerState.Ready>(
            startUseCase(LessonId("lesson1"), Difficulty.EASY),
        )
        var nowMs = 1_000_000L
        while (state.indexInPool < state.playOrder.size) {
            state = submitAnswer(state, UserAnswer.SingleChoiceAnswer(optId("A")), nowMs)
            nowMs += 1_000
        }
        return assertIs<RunnerState.Completed>(completeUseCase(state))
    }

    private fun stockQuestion(id: String, order: Int, payload: String) = Question(
        id = QuestionId(id),
        lessonId = LessonId("lesson1"),
        text = "Question $id",
        payload = payload,
        language = "en",
        order = order,
        version = 1L,
        lastModifiedAt = 1000L,
    )
}

/**
 * Локальный запас вопросов без сети: чтение кэша; сетевой по форме метод считает вызовы
 * и отказывает — тест, дошедший до него, немедленно виден и в счётчике.
 */
private class PreparedStockQuestions(private val stock: List<Question>) : QuestionRepository {
    var refreshCalls: Int = 0
        private set

    override fun observeByLesson(lessonId: LessonId): Flow<List<Question>> = flow {
        emit(stock.filter { it.lessonId == lessonId }.sortedBy { it.order })
    }

    override suspend fun getById(id: QuestionId): Question? = stock.find { it.id == id }

    override suspend fun refreshByParents(lessonIds: Set<LessonId>, cursor: Long): Result<Unit> {
        refreshCalls++
        return Result.failure(IllegalStateException("offline: сетевого гейта на старте быть не должно"))
    }
}

/** Локальные уроки без сети: сетевой по форме метод считает вызовы и отказывает. */
private class LocalLessons(private val lessons: Map<String, Lesson>) : LessonRepository {
    var refreshCalls: Int = 0
        private set

    override fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>> = flow {
        emit(lessons.values.filter { it.themeId == themeId }.sortedBy { it.order })
    }

    override suspend fun getById(id: LessonId): Lesson? = lessons[id.value]

    override suspend fun refreshByParents(
        themeIds: Set<ThemeId>,
        cursor: Long,
    ): Result<Set<LessonId>> {
        refreshCalls++
        return Result.failure(IllegalStateException("offline: сетевого гейта на старте быть не должно"))
    }
}
