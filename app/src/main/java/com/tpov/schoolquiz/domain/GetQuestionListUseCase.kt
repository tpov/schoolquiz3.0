package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class GetQuestionListUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke() = repositoryDB.getQuestionList()
}