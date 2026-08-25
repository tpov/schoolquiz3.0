package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer

/**
 * What to study next, worked out from the attempt that just finished.
 *
 * A percentage says how it went and nothing about what to do about it. This names the questions
 * that cost the most and points at the lesson that lays their groundwork, so a poor run ends with
 * somewhere to go rather than a number to feel bad about.
 */
data class ResultAdvice(
    /** How many shown questions scored below half marks. */
    val weakAnswers: Int,
    /** The lesson to study first, or null when this is already the earliest in its theme. */
    val suggestedLessonTitle: String?,
)

/**
 * Half marks. Digit '5' is 50%, and anything under it left more on the table than it took.
 *
 * Chosen on the digit rather than the derived percent so the rule reads off exactly what was
 * stored: no rounding sits between the answer and the advice.
 */
private const val WEAK_ANSWER_DIGIT = '5'

/**
 * Advice for a finished attempt, or null when there is nothing worth saying.
 *
 * Silence is the right answer more often than not. A run with every shown answer at half marks or
 * better does not need a lesson recommended, and neither does a theme with nothing earlier in it —
 * advice that points nowhere is worse than none, because it still asks to be read.
 *
 * @param earlierLessonTitles titles before this lesson in its theme, in the order they are taught.
 */
fun resultAdvice(
    codeAnswer: CodeAnswer,
    earlierLessonTitles: List<String>,
): ResultAdvice? {
    val shown = codeAnswer.raw.filter { it != '0' }
    val weak = shown.count { it < WEAK_ANSWER_DIGIT }
    if (weak == 0) return null
    // The one taught immediately before, not the first of the theme: it is the nearest thing this
    // lesson builds on, and the likeliest gap behind a weak run.
    return ResultAdvice(weakAnswers = weak, suggestedLessonTitle = earlierLessonTitles.lastOrNull())
}

/**
 * "1 ответ", "2 ответа", "5 ответов" — Russian counts three ways.
 *
 * The teens are the trap that catches naive versions of this: eleven takes the same form as five,
 * not the same form as one. Kept next to the rule that produces the number so the two cannot drift.
 */
fun weakAnswersWording(count: Int): String {
    val tail = count % 100
    val last = count % 10
    val teens = tail in 11..14
    val noun =
        when {
            teens -> "ответов"
            last == 1 -> "ответ"
            last in 2..4 -> "ответа"
            else -> "ответов"
        }
    val verb = if (last == 1 && !teens) "набрал" else "набрали"
    return "$count $noun $verb меньше половины балла"
}
