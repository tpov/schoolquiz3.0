package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResultAdviceTest {
    private val earlier = listOf("Что такое хеш", "Коллизии", "Соль в паролях")

    /** A clean run needs no advice: pointing somewhere would imply something went wrong. */
    @Test
    fun perfectAttemptGetsNoAdvice() {
        assertNull(resultAdvice(CodeAnswer("999"), earlier))
    }

    /** Half marks is not weak. The rule fires below the halfway digit, not at it. */
    @Test
    fun halfMarksIsNotCountedAsWeak() {
        assertNull(resultAdvice(CodeAnswer("555"), earlier))
    }

    @Test
    fun countsOnlyAnswersBelowHalf() {
        val advice = resultAdvice(CodeAnswer("192"), earlier)

        assertEquals(2, advice?.weakAnswers)
    }

    /** Questions never put to the player cannot have been answered badly. */
    @Test
    fun skippedQuestionsAreNotWeakAnswers() {
        assertNull(resultAdvice(CodeAnswer("090"), earlier))
    }

    /** The lesson taught immediately before is the nearest thing this one builds on. */
    @Test
    fun suggestsTheLessonTaughtJustBefore() {
        val advice = resultAdvice(CodeAnswer("111"), earlier)

        assertEquals("Соль в паролях", advice?.suggestedLessonTitle)
    }

    /**
     * The first lesson of a theme still reports the weak answers, with nowhere to send anybody.
     *
     * Saying "four answers went badly" is useful on its own; inventing a lesson to blame is not.
     */
    @Test
    fun theFirstLessonOfAThemeHasNothingToSuggest() {
        val advice = resultAdvice(CodeAnswer("1111"), emptyList())

        assertEquals(4, advice?.weakAnswers)
        assertNull(advice?.suggestedLessonTitle)
    }
}

class WeakAnswersWordingTest {
    @Test
    fun oneAnswerTakesTheSingular() {
        assertEquals("1 ответ набрал меньше половины балла", weakAnswersWording(1))
    }

    @Test
    fun twoToFourTakeTheFewForm() {
        assertEquals("3 ответа набрали меньше половины балла", weakAnswersWording(3))
    }

    @Test
    fun fiveAndAboveTakeThePluralForm() {
        assertEquals("7 ответов набрали меньше половины балла", weakAnswersWording(7))
    }

    /** The teens are the trap: eleven counts like five, not like one. */
    @Test
    fun theTeensCountLikeMany() {
        assertEquals("11 ответов набрали меньше половины балла", weakAnswersWording(11))
        assertEquals("14 ответов набрали меньше половины балла", weakAnswersWording(14))
    }

    /** Past the teens the pattern resumes: twenty-one is singular again. */
    @Test
    fun theCycleResumesAfterTheTeens() {
        assertEquals("21 ответ набрал меньше половины балла", weakAnswersWording(21))
        assertEquals("22 ответа набрали меньше половины балла", weakAnswersWording(22))
    }
}
