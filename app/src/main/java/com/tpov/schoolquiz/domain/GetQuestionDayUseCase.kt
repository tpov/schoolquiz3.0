package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class GetQuestionDayUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke() = repositoryDB.getApiQuestionList()
}