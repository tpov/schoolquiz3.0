package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class GetQuizByIdUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke(idQuiz: Int, tpovId: Int) = repositoryDB.getQuizById(idQuiz, tpovId)
}