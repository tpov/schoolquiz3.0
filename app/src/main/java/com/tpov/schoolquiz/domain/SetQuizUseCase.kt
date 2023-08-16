package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class SetQuizUseCase @Inject constructor(private val repositoryFB: RepositoryFB) {
    suspend operator fun invoke() = repositoryFB.setAllQuiz()
}