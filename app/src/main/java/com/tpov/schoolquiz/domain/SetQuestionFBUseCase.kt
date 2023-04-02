package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class SetQuestionFBUseCase @Inject constructor(private val repositoryFB: RepositoryFB) {
operator fun invoke(tpovId: Int) = repositoryFB.setQuestionData(tpovId)
}