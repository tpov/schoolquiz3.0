package com.tpov.schoolquiz.domain

import android.content.Context
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class GetQuiz8FBUseCase @Inject constructor(private val repositoryFB: RepositoryFB) {
    operator fun invoke(context: Context) = repositoryFB.getQuiz8Data(context)
}