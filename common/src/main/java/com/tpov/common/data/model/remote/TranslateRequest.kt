package com.tpov.common.data.model.remote

data class TranslateRequest(
            val question: QuestionRemote,
            val idQuiz: Int,
            val usePaidTranslation: Boolean,
            val toLang: String,
    val event: Int
        )