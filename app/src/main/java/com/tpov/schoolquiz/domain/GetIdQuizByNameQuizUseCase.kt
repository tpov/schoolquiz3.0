package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class GetIdQuizByNameQuizUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke(nameQuiz: String, tpovId: Int) = repositoryDB.getIdQuizByNameQuiz(nameQuiz, tpovId)
}