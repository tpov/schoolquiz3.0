package com.tpov.common.data.model.remote

import com.google.firebase.firestore.IgnoreExtraProperties
import com.tpov.common.data.model.local.QuestionEntity

@IgnoreExtraProperties
data class QuestionRemote (
    val nameQuestion: String,
    val answer: Int,
    val nameAnswers: String,
    val pathPictureQuestion: String?,
    val lvlTranslate: Int,
    val numQuestion: Int,
    val hardQuestion: Boolean,
    val language: String
) {
    fun toQuizEntity(
        id: Int? = null,
        idQuiz: Int,
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
        "", 0, "", "",  0, 0 ,false, ""
    )
}
