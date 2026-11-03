package com.tpov.common.presentation.question

import com.tpov.common.data.model.local.QuestionLocal

sealed class QuestionListResult {
    data class Success(val questions: List<QuestionLocal>) : QuestionListResult()
    data object EmptyTranslation : QuestionListResult()
    data class Error(val message: String) : QuestionListResult()
}
