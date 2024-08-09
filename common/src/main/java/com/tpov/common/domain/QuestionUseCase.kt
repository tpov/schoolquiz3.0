package com.tpov.common.domain

import com.tpov.common.domain.repository.RepositoryQuestion
import javax.inject.Inject

class QuestionUseCase @Inject constructor(private val repositoryQuestion: RepositoryQuestion) {
    suspend fun fetchQuestion(
        typeId: Int,
        categoryId: String,
        subcategoryId: String,
        pathLanguage: String,
        idQuiz: Int
    ) = repositoryQuestion.fetchQuestion(
        typeId,
        categoryId,
        subcategoryId,
        pathLanguage,
        idQuiz
    )
}