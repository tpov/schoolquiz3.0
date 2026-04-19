package com.tpov.common.data.model.local

import com.tpov.common.CODE_EMPTY_ANSWER
import com.tpov.common.data.model.entity.QuestionDetailEntity
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.DateUtil

data class QuestionDetailLocal(
    val id: Int? = null,
val pathStructure: PathStructure = PathStructure(),
    val data: String = "",
    val codeAnswer: String? = "",
    val hardQuiz: Boolean = false,
    val sync : Boolean = false
) {
    fun create(pathStructure: PathStructure, numQuestions: Int) =  QuestionDetailLocal(
        0,
        pathStructure,
        DateUtil().getDateQuiz(),
        CODE_EMPTY_ANSWER.toString().repeat(numQuestions),
        hardQuiz,
        sync
    )

    fun toQuestionDetailEntity() = QuestionDetailEntity(id,
        eventName = pathStructure.nameEvent,
        categoryName = pathStructure.nameCategory,
        subCategoryName = pathStructure.nameSubCategory,
        subsubCategoryName = pathStructure.nameSubsubCategory,
        quizName = pathStructure.nameQuiz,
        data = data,
        codeAnswer = codeAnswer, hardQuiz = hardQuiz,
        synth = sync
        )
}
