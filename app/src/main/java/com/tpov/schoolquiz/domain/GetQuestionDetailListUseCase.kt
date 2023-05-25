package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class GetQuestionDetailListUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    suspend operator fun invoke() = repositoryDB.getQuestionDetailList()
}