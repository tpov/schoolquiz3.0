package com.tpov.schoolquiz.domain

import android.content.Context
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class GetQuiz1FBUseCase @Inject constructor(private val repositoryFB: RepositoryFB) {
    operator fun invoke(tpovId: Int) = repositoryFB.getQuiz1Data(tpovId)
}