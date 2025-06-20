package com.tpov.common.data.model.local

import com.tpov.common.data.model.entity.QuestionEntity
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils


data class QuestionLocal(
    var id: Int? = null,
    var numQuestion: Int = 0,
    var nameQuestion: String = "",
    var pathPictureQuestion: String? = "",
    var nameAnswers: String = "",
    var hardQuestion: Boolean = false,
    var pathStructure: PathStructure = PathStructure(),
    var language: LanguageUtils = LanguageUtils.ENGLISH,
    var lvlTranslate: Int = 0
) {
    fun toQuestionEntity() = QuestionEntity(
        id,
        numQuestion,
        nameQuestion,
        pathPictureQuestion,
        nameAnswers,
        hardQuestion,
        pathStructure.nameEvent,
        pathStructure.nameCategory,
        pathStructure.nameSubCategory,
        pathStructure.nameSubsubCategory,
        pathStructure.nameQuiz,
        language = this.language.fullName,
        lvlTranslate
    )
}
