package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.ApiQuestion
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class InsertApiQuestionListUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke(apiQuestion: List<ApiQuestion>) = repositoryDB.insertListApiQuestion(apiQuestion)
}