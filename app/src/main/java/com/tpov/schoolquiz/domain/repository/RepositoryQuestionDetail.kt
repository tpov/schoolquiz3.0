package com.tpov.schoolquiz.domain.repository

import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity

interface RepositoryQuestionDetail {
    fun insertQuestionDetail(questionDetail: QuestionDetailEntity)

    fun getQuestionDetailList(): List<QuestionDetailEntity>

    fun updateQuestionDetail(questionDetail: QuestionDetailEntity)

    fun deleteQuestionDetailByIdQuiz(idQuiz: Int)
}