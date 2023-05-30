package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class UpdateQuestionUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    suspend operator fun invoke(questionEntity: QuestionEntity) = repositoryDB.updateQuestion(questionEntity)
}