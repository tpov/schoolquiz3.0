package com.tpov.schoolquiz.presentation.create_quiz

import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity.Companion.REGIME_CREATE_QUIZ
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity.Companion.REGIME_EDIT_ARCHIVE_QUIZ
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity.Companion.REGIME_EDIT_QUIZ
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity.Companion.REGIME_TRANSLATE_QUIZ
import com.tpov.schoolquiz.presentation.create_quiz.strategy.CreateHandler
import com.tpov.schoolquiz.presentation.create_quiz.strategy.EditCompareHandler
import com.tpov.schoolquiz.presentation.create_quiz.strategy.EditHandler
import com.tpov.schoolquiz.presentation.create_quiz.strategy.TranslateHandler

class RegimeHandlerImpl(private val activity: CreateQuizActivity) {
    fun handler(): RegimeHandler {
        val regimeHandlers = mapOf(
            REGIME_CREATE_QUIZ to CreateHandler(activity),
            REGIME_EDIT_QUIZ to EditHandler(activity),
            REGIME_EDIT_ARCHIVE_QUIZ to EditCompareHandler(activity),
            REGIME_TRANSLATE_QUIZ to TranslateHandler(activity)
        )
        return regimeHandlers[activity.regime] ?: throw IllegalArgumentException("Unsupported regime")
    }
}
