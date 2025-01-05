package com.tpov.schoolquiz.presentation.create_quiz

import com.tpov.schoolquiz.presentation.create_quiz.strategy.CreateHandler
import com.tpov.schoolquiz.presentation.create_quiz.strategy.EditCompareHandler
import com.tpov.schoolquiz.presentation.create_quiz.strategy.EditHandler
import com.tpov.schoolquiz.presentation.create_quiz.strategy.TranslateHandler

object RegimeHandlerImpl: CreateQuizActivity() {
    fun handler(): RegimeHandler {
        val regimeHandlers = mapOf(
            REGIME_CREATE_QUIZ to CreateHandler(),
            REGIME_EDIT_QUIZ to EditHandler(),
            REGIME_EDIT_ARCHIVE_MY_QUIZ to EditArchivedOtherHandler(),
            REGIME_EDIT_ARCHIVE_QUIZ to EditCompareHandler(),
            REGIME_TRANSLATE_QUIZ to TranslateHandler()
        )
        return regimeHandlers[viewModel.regime] ?: null!!
    }
}