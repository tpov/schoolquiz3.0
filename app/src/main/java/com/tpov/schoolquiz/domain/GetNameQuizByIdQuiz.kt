package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class GetNameQuizByIdQuiz @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke(id: Int) = repositoryDB.getNameQuizByIdQuiz(id)
}