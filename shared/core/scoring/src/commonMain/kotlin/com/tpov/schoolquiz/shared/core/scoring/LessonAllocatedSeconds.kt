package com.tpov.schoolquiz.shared.core.scoring

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Сколько времени урок отводит игроку на одной сложности — величина, из которой считаются и
 * награда, и цена открытия.
 *
 * Зеркало `lessonAllocatedSeconds` из `functions/lesson-reward.js`. [UnlockPricing] переводит эту
 * величину в нолики, но взять её было неоткуда: цену знал только сервер, и замок в списке уроков
 * рисовался без числа. AD-3 требует, чтобы отложенное действие выводилось из синхронизированного
 * контента — вопросы урока на устройстве уже лежат, и этого достаточно.
 *
 * Считается то, что игроку действительно покажут, а не то, что лежит в коллекции: вопросы другой
 * сложности пропускаются, архивные исчезают, переводы одного вопроса схлопываются в один, а сверх
 * набора, который тянет раннер, значение несёт среднее — какие двадцать выпадут, решает случай, и
 * стоимость урока от этого зависеть не должна.
 *
 * Цену по-прежнему **назначает сервер**: он читает вопросы сам, потому что вызывающий, который мог
 * бы назвать свою цену, назвал бы ноль. Здесь она вычисляется, чтобы показать её игроку до
 * покупки; расхождение двух реализаций — ошибка, и её ловит общий набор фикстур.
 */
object LessonAllocatedSeconds {

    /**
     * Вопрос в том виде, в каком он влияет на цену.
     *
     * Отдельный тип, а не доменный `Question`: `shared/core/scoring` не знает о фичах, а всё, что
     * нужно формуле, — это идентификатор, объём и сложность.
     */
    data class Question(
        val id: String,
        /** Знаки вопроса вместе с вариантами; картинка стоит [CHARS_PER_IMAGE] знаков. */
        val charsCount: Int,
        /**
         * Сложность или `null`, если её не удалось прочитать.
         *
         * `null` считается лёгкой — так поступает сервер (`String(difficulty || "EASY")`), а цена
         * обязана совпадать с той, что он спишет. Из локальной базы `null` прийти не может:
         * там сложность — перечисление.
         */
        val difficulty: Difficulty?,
        val archived: Boolean = false,
    )

    /**
     * Отведённое время всего урока на сложности [hard].
     *
     * @param questions все вопросы урока, обеих сложностей, включая архивные и переводы.
     */
    fun of(
        questions: List<Question>,
        hard: Boolean,
    ): Long {
        val wanted = if (hard) Difficulty.HARD else Difficulty.EASY
        val seenCanonical = mutableSetOf<String>()
        val seconds = mutableListOf<Long>()
        for (question in questions) {
            if (question.archived) continue
            if ((question.difficulty ?: Difficulty.EASY) != wanted) continue
            if (!seenCanonical.add(canonicalQuestionId(question.id))) continue
            seconds += secondsFor(question.charsCount, hard)
        }
        if (seconds.isEmpty()) return 0L
        if (seconds.size <= POOL_SIZE) return seconds.sum()
        // Сверх набора значение несёт среднее: какие двадцать вытянет раннер — дело случая.
        return (seconds.sum().toDouble() / seconds.size * POOL_SIZE).roundToLong()
    }

    /**
     * Отведённое время одного вопроса.
     *
     * Повторяет `computeTimer` из `RunnerLogic.kt` в режиме обучения: сложный вопрос читается
     * быстрее в пересчёте на знак, потому что над ним положено думать, а не читать его.
     */
    internal fun secondsFor(
        charsCount: Int,
        hard: Boolean,
    ): Long {
        val k = if (hard) TIMER_K_HARD else TIMER_K_EASY
        return max(MIN_QUESTION_SECONDS, (max(0, charsCount) * k).roundToLong())
    }

    /**
     * Исходный идентификатор перевода: `q1__ru` и `q1__en` — один вопрос.
     *
     * Повторяет `dedupeTranslatedVariants` из `StartLessonAttemptUseCase.kt`. Урок, переведённый на
     * три языка, держит по три документа на вопрос, а показывает раннер один: считать все три
     * значило бы утроить и то, что урок стоит, и то, что он платит.
     */
    internal fun canonicalQuestionId(id: String): String {
        val separator = id.lastIndexOf("__")
        if (separator <= 0 || separator >= id.length - 3) return id
        val suffix = id.substring(separator + 2)
        // Только латиница и дефис — ровно `[A-Za-z-]` сервера. `isLetter()` принял бы и кириллицу,
        // и `q1__ру` схлопнулся бы здесь, но не там: урок стоил бы на устройстве меньше, чем спишут.
        val isLanguage = suffix.length in 2..8 && suffix.all { it in 'A'..'Z' || it in 'a'..'z' || it == '-' }
        return if (isLanguage) id.substring(0, separator) else id
    }

    /** Картинка стоит столько знаков. `IMAGE_CHARS` в `lesson-reward.js`. */
    const val CHARS_PER_IMAGE: Int = 100

    /** Больше стольких вопросов раннер за одну попытку не показывает. */
    const val POOL_SIZE: Int = 20

    private const val TIMER_K_EASY = 0.36
    private const val TIMER_K_HARD = 0.24
    private const val MIN_QUESTION_SECONDS = 5L
}
