package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity
import com.tpov.schoolquiz.domain.repository.RepositoryQuestionDetail
import javax.inject.Inject

class QuestionDetailUserCase @Inject constructor(private val repositoryQuestionDetail: RepositoryQuestionDetail) {
    fun insertQuestionDetail(questionDetail: QuestionDetailEntity) =
        repositoryQuestionDetail.insertQuestionDetail(questionDetail)

    fun getQuestionDetailList() = repositoryQuestionDetail.getQuestionDetailList()


    fun updateQuestionDetail(questionDetail: QuestionDetailEntity) =
        repositoryQuestionDetail.updateQuestionDetail(questionDetail)

    fun deleteQuestionDetailByIdQuiz(idQuiz: Int) = repositoryQuestionDetail.deleteQuestionDetailByIdQuiz(idQuiz)
}