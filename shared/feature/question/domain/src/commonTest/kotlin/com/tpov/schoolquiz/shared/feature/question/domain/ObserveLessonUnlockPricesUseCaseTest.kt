package com.tpov.schoolquiz.shared.feature.question.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.use_case.ObserveLessonUnlockPricesUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Цена открытия урока, посчитанная из его же вопросов.
 *
 * До сих пор её знал только сервер, и замок в списке уроков рисовался без числа. Здесь проверяется
 * не сама формула — её пиннит общий набор фикстур в `shared/core/scoring` — а то, что use case
 * берёт нужные вопросы и не берёт лишних.
 */
class ObserveLessonUnlockPricesUseCaseTest {

    /** Разбирает payload вида `difficulty|chars`: формуле нужны только эти две величины. */
    private class SizeParser : QuestionContentParser {
        override fun parse(payload: String): Result<QuestionContent> {
            val (difficulty, chars) = payload.split("|")
            if (chars.toInt() < 0) return Result.failure(IllegalArgumentException("нечитаемый payload"))
            return Result.success(
                QuestionContent.SingleChoice(
                    id = "q",
                    difficulty = Difficulty.valueOf(difficulty),
                    text = "x".repeat(chars.toInt()),
                    imageUrl = null,
                    options = listOf(option("a"), option("b")),
                    correctOptionId = OptionId("a"),
                ),
            )
        }

        private fun option(id: String) = QuestionContent.Option(OptionId(id), text = "")
    }

    private val useCase = { repo: FakeQuestionRepository -> ObserveLessonUnlockPricesUseCase(repo, SizeParser()) }

    private fun question(
        id: String,
        lesson: String,
        difficulty: Difficulty = Difficulty.EASY,
        chars: Int = 120,
    ) = Question(
        id = QuestionId(id),
        lessonId = LessonId(lesson),
        text = "Вопрос $id",
        payload = "${difficulty.name}|$chars",
        language = "ru",
        order = 0,
        version = 1L,
        lastModifiedAt = 0L,
    )

    @Test
    fun `given lessons with questions then each one is priced`() = runTest {
        val repo = FakeQuestionRepository(
            listOf(
                question("q1", "l1"),
                question("q2", "l1", Difficulty.HARD),
                question("q3", "l2"),
            ),
        )

        val prices = useCase(repo)(listOf(LessonId("l1"), LessonId("l2"))).first()

        assertEquals(setOf(LessonId("l1"), LessonId("l2")), prices.keys)
        // Урок со сложным вопросом дороже: сложная половина тарифицируется втрое.
        assertTrue(
            prices.getValue(LessonId("l1")) > prices.getValue(LessonId("l2")),
            "сложный вопрос обязан удорожать урок: ${prices[LessonId("l1")]} против ${prices[LessonId("l2")]}",
        )
    }

    @Test
    fun `given a lesson whose questions have not arrived then it has no price`() = runTest {
        // Ноль обещал бы бесплатную дверь, единица — дешёвую; обе разошлись бы со списанием.
        val repo = FakeQuestionRepository(listOf(question("q1", "l1")))

        val prices = useCase(repo)(listOf(LessonId("l1"), LessonId("l-empty"))).first()

        assertNull(prices[LessonId("l-empty")])
    }

    @Test
    fun `given no lessons then nothing is observed at all`() = runTest {
        // combine пустого списка потоков не отдаёт ничего и подвесил бы вызывающего навсегда.
        assertEquals(emptyMap(), useCase(FakeQuestionRepository())(emptyList()).first())
    }

    @Test
    fun `given an unreadable payload then the question still costs its minimum`() = runTest {
        // Пропустить нечитаемый вопрос значило бы показать урок дешевле, чем он будет стоить.
        // Один знак — тот же минимум в пять секунд, что и у вопроса нулевого объёма.
        val readable = FakeQuestionRepository(listOf(question("q1", "l1", chars = 1)))
        val unreadable = FakeQuestionRepository(listOf(question("q1", "l1", chars = -1)))

        assertEquals(
            useCase(readable)(listOf(LessonId("l1"))).first()[LessonId("l1")],
            useCase(unreadable)(listOf(LessonId("l1"))).first()[LessonId("l1")],
        )
    }
}
