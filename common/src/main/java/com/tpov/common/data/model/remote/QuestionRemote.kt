package com.tpov.common.data.model.remote

import com.google.firebase.firestore.IgnoreExtraProperties
import com.tpov.common.data.model.local.QuestionEntity

@IgnoreExtraProperties
data class QuestionRemote (
    val nameQuestion: String,
    val answer: Int,
    val nameAnswers: String,
    val pathPictureQuestion: String?,
    val lvlTranslate: Int
) {
    fun toQuizEntity(
        numQuestion: Int,
        id: Int = 0,
        hardQuestion: Boolean,
        idQuiz: Int,
        language: String
    ): QuestionEntity {
        return QuestionEntity(
            id = id,
            numQuestion = numQuestion,
            nameQuestion = this.nameQuestion,
            pathPictureQuestion = pathPictureQuestion,
            answer = answer,
            nameAnswers = nameAnswers,
            hardQuestion = hardQuestion,
            idQuiz = idQuiz,
            language = language,
            lvlTranslate = lvlTranslate,
        )
    }

    constructor() : this(
        "", 0, "", "",  0
    )
}
