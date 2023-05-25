package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class UpdateQuestionDetailUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    suspend operator fun invoke(questionDetailEntity: QuestionDetailEntity) = repositoryDB.updateQuestionDetail(questionDetailEntity)
}