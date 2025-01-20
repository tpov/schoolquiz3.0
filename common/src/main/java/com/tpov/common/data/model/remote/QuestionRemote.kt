package com.tpov.common.data.model.remote

import com.google.firebase.firestore.IgnoreExtraProperties
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.presentation.model.PathStructure

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
    fun toQuestionEntity(
        pathStructure: PathStructure
    ): QuestionEntity {
        return QuestionEntity(
            id = null,
            numQuestion = numQuestion,
            nameQuestion = this.nameQuestion,
            pathPictureQuestion = pathPictureQuestion,
            answer = answer,
            nameAnswers = nameAnswers,
            hardQuestion = hardQuestion,
            idEvent = pathStructure.idEvent,
            idQuiz = pathStructure.idQuiz,
            idCategory = pathStructure.idCategory,
            idSubCategory = pathStructure.idSubCategory,
            idSubsubCategory = pathStructure.idSubsubCategory,
            language = language,
            lvlTranslate = lvlTranslate
        )
    }

    constructor() : this(
        "", 0, "", "",  0, 0 ,false, ""
    )
}
