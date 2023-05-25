package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class GetQuestionListByIdQuiz @Inject constructor(private val repositoryDB: RepositoryDB) {
    suspend operator fun invoke(id: Int) = repositoryDB.getQuestionListByIdQuiz(id)
}