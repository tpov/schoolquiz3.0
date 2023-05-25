package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class GetQuizByIdUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    suspend operator fun invoke(idQuiz: Int) = repositoryDB.getQuizById(idQuiz)
}