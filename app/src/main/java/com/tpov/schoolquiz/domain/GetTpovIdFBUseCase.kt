package com.tpov.schoolquiz.domain

import android.content.Context
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class GetTpovIdFBUseCase @Inject constructor(private val repositoryFB: RepositoryFB) {
    operator fun invoke(context: Context) = repositoryFB.getTpovIdFB(context)
}