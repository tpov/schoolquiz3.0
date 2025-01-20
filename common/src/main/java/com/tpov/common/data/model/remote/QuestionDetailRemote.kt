package com.tpov.common.data.model.remote

import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.presentation.model.PathStructure

data class QuestionDetailRemote(
    val data: String,
    val codeAnswer: String?,
    val hardQuiz: Boolean
) {
    fun toQuestionDetailEntity(pathStructure: PathStructure, id: Int? = null, synth: Boolean = true) =
        QuestionDetailEntity(
            id = id,
            idEvent = pathStructure.idEvent,
            idCategory = pathStructure.idCategory,
            idSubCategory = pathStructure.idSubCategory,
            idSubsubCategory = pathStructure.idSubsubCategory,
            idQuiz = pathStructure.idQuiz,
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

