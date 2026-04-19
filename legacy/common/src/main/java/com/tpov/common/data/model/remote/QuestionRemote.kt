package com.tpov.common.data.model.remote

import com.google.firebase.firestore.IgnoreExtraProperties
import com.tpov.common.data.model.entity.QuestionEntity
import com.tpov.common.presentation.model.PathStructure

@IgnoreExtraProperties
data class QuestionRemote (
    val nameQuestion: String,
    val nameAnswers: String,
    val pathPictureQuestion: String?,
    val infoQuestion: String,
    val lvlTranslate: Int,
    val numQuestion: Int,
    val hardQuestion: Boolean,
    val language: String,
    val nameCategory: String,
    val nameSubCategory: String,
    val nameSubsubCategory: String,
    val nameQuiz: String
) {
    fun toQuestionEntity(
        event: PathStructure
    ): QuestionEntity {
        return QuestionEntity(
            id = null,
            numQuestion = numQuestion,
            nameQuestion = this.nameQuestion,
            infoQuestion = this.infoQuestion,
            pathPictureQuestion = pathPictureQuestion,
            nameAnswers = nameAnswers,
            hardQuestion = hardQuestion,
            eventName = event.nameEvent,
            quizName = nameQuiz,
            categoryName = nameCategory,
            subCategoryName = nameSubCategory,
            subsubCategoryName = nameSubsubCategory,
            language = language,
            lvlTranslate = lvlTranslate
        )
    }

    constructor() : this(
        "", "", "", "", 0, 0 ,false, "", "","","",""
    )
}
