package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class InsertQuizUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke(quizEntity: QuizEntity) = repositoryDB.insertQuiz(quizEntity)
}