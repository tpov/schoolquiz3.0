package com.tpov.common.data.model.remote

import com.google.firebase.firestore.IgnoreExtraProperties
import com.tpov.common.data.model.entity.QuestionEntity
import com.tpov.common.presentation.model.PathStructure

@IgnoreExtraProperties
data class QuestionRemote (
    val nameQuestion: String,
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
            nameAnswers = nameAnswers,
            hardQuestion = hardQuestion,
            event = pathStructure.nameEvent,
            quiz = pathStructure.nameQuiz,
            category = pathStructure.nameCategory,
            subCategory = pathStructure.nameSubCategory,
            subsubCategory = pathStructure.nameSubsubCategory,
            language = language,
            lvlTranslate = lvlTranslate
        )
    }

    constructor() : this(
        "", "", "",  0, 0 ,false, ""
    )
}
