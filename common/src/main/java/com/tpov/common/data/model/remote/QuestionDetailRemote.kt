package com.tpov.common.data.model.remote

import com.tpov.common.data.model.local.QuestionDetailEntity

data class QuestionDetailRemote(
    val data: String,
    val codeAnswer: String?,
    val hardQuiz: Boolean
) {
    fun toQuestionDetailEntity(id: Int? = null,idQuiz: Int,synth: Boolean = true) =
        QuestionDetailEntity(
            id = id,
            idQuiz = idQuiz,
            data = data,
            codeAnswer = codeAnswer,
            hardQuiz = hardQuiz,
            synth = synth
        )

    constructor() : this(
        data = "",
        codeAnswer = null,
        hardQuiz = false
    )
}

