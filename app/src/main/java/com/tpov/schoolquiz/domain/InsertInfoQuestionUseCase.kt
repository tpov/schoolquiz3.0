package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class InsertInfoQuestionUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {

    operator fun invoke(questionDetailEntity: QuestionDetailEntity) =
        repositoryDB.insertQuizDetail(questionDetailEntity)
}