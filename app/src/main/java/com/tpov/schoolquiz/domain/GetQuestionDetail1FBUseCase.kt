package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class GetQuestionDetail1FBUseCase @Inject constructor(private val repositoryFB: RepositoryFB) {
    operator fun invoke() = repositoryFB.getQuestionDetail1()
}