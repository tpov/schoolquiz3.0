package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.ApiQuestion
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class UpdateQuestionDayUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    suspend operator fun invoke(apiQuestion: ApiQuestion) = repositoryDB.updateApiQuestion(apiQuestion)
}