package com.tpov.common.data.model.remote

import com.tpov.common.data.model.entity.QuestionDetailEntity
import com.tpov.common.presentation.model.PathStructure

/**
 *Используется для:
 * 1. подсчет глобального результата сервертой функцией
 * 2. подсчет локального результата клиентом
 * 3. подсчет статистики квеста*
 * 4. составление таблицы лидеров серверной функцией
 * 5.
 */
data class QuestionDetailRemote(
    val data: String,
    val codeAnswer: String?,
    val hardQuiz: Boolean,
    val nameCategory: String,
    val nameSubCategory: String,
    val nameSubsubCategory: String,
    val nameQuiz: String
) {
    fun toQuestionDetailEntity(pathStructure: PathStructure, id: Int? = null, synth: Boolean = true) =
        QuestionDetailEntity(
            id = id,
            eventName = pathStructure.nameEvent,
            categoryName = nameCategory,
            subCategoryName = nameSubCategory,
            subsubCategoryName = nameSubsubCategory,
            quizName = nameQuiz,
            data = data,
            codeAnswer = codeAnswer,
            hardQuiz = hardQuiz,
            synth = synth
        )

    constructor() : this(
        data = "",
        codeAnswer = null,
        hardQuiz = false,
        nameCategory = "",
        nameSubCategory = "",
        nameSubsubCategory = "",
        nameQuiz = ""
    )
}


