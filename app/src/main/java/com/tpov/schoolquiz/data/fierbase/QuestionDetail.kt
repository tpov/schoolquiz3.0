package com.tpov.schoolquiz.data.fierbase

import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity

data class QuestionDetail(
    val data: String,
    val codeAnswer: String?,
    val hardQuiz: Boolean,
    val nameUser: String,
    val rating: Int
){
    constructor() : this(
        data = "",
        codeAnswer = null,
        hardQuiz = false,
        nameUser = "",
        rating = 0
    )
}


fun QuestionDetail.toQuestionDetailEntity(id: Int? = null, idQuiz: Int, synthFB: Boolean): QuestionDetailEntity {
    return QuestionDetailEntity(
        id = id,
        idQuiz = idQuiz,
        data = this.data,
        codeAnswer = this.codeAnswer,
        hardQuiz = this.hardQuiz,
        synthFB = synthFB
    )
}
