package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class InsertQuestionUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke(questionEntity: QuestionEntity) = repositoryDB.insertQuestion(questionEntity)
}