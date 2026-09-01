package com.tpov.schoolquiz.shared.feature.question.domain.use_case

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.core.scoring.LessonAllocatedSeconds
import com.tpov.schoolquiz.shared.core.scoring.UnlockPricing
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Во сколько ноликов обойдётся открыть каждый из этих уроков.
 *
 * Замок в списке уроков рисовался без числа: цену знал только сервер, и спросить её было нельзя,
 * не купив. AD-3 требует обратного — отложенное действие обязано выводиться из синхронизированного
 * контента, а вопросы урока на устройстве уже лежат.
 *
 * Списывает по-прежнему сервер, и он считает цену по своим вопросам, а не по названной клиентом:
 * вызывающий, который мог бы назвать свою цену, назвал бы ноль. Здесь то же число получается ради
 * показа, и совпадение двух реализаций держит общий набор фикстур
 * (`config/lesson-allocated-seconds-fixtures.json`).
 *
 * Урок, чьи вопросы ещё не доехали, цены не получает вовсе — ноль был бы обещанием бесплатной
 * двери, а единица (минимум шкалы) — обещанием дешёвой.
 */
class ObserveLessonUnlockPricesUseCase(
    private val questionRepository: QuestionRepository,
    private val parser: QuestionContentParser,
) {

    /**
     * @param lessonIds уроки, чью цену нужно показать. Пустой список — пустая карта, без подписок.
     * @return цена в ноликах по идентификатору урока; урока без вопросов в карте нет.
     */
    operator fun invoke(lessonIds: List<LessonId>): Flow<Map<LessonId, Int>> {
        if (lessonIds.isEmpty()) return flowOf(emptyMap())
        val flows = lessonIds.map { id -> questionRepository.observeByLesson(id).map { id to it } }
        return combine(flows) { pairs ->
            pairs.mapNotNull { (id, questions) -> priceOf(questions)?.let { id to it } }.toMap()
        }.distinctUntilChanged()
    }

    private fun priceOf(questions: List<Question>): Int? {
        if (questions.isEmpty()) return null
        val priced = questions.map { it.toPriced() }
        val price =
            UnlockPricing.price(
                kind = UnlockPricing.Kind.LESSON,
                easyAllocatedSeconds = LessonAllocatedSeconds.of(priced, hard = false),
                hardAllocatedSeconds = LessonAllocatedSeconds.of(priced, hard = true),
            )
        return price.toInt()
    }

    /**
     * Вопрос в том виде, в каком он влияет на цену.
     *
     * Читается [QuestionContentParser.parseForDisplay], а не `parse`: цене нужен объём и сложность,
     * а ключ ответа — нет, и урок, приехавший закрытым, отдаёт как раз урезанную половину.
     *
     * Нечитаемый payload считается лёгким вопросом нулевого объёма — ровно тем минимумом в пять
     * секунд, который назначает и сервер. Пропустить его значило бы показать урок дешевле, чем он
     * будет стоить при покупке.
     */
    private fun Question.toPriced(): LessonAllocatedSeconds.Question {
        val display =
            parser.parseForDisplay(
                payload = payload,
                fallbackId = id.value,
                fallbackText = text,
                fallbackDifficulty = Difficulty.EASY,
            ).getOrNull()
        return LessonAllocatedSeconds.Question(
            id = id.value,
            charsCount = display?.charsCount ?: 0,
            difficulty = display?.difficultyOrNull,
            // Архивные вопросы сюда не доезжают: их отсекает сам запрос к базе.
            archived = false,
        )
    }
}
