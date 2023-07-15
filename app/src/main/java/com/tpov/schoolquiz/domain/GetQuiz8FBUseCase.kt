package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class GetQuiz8FBUseCase @Inject constructor(private val repositoryFB: RepositoryFB) {
    suspend operator fun invoke() = repositoryFB.getQuiz8Data()
}