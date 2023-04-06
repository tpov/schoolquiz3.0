package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class GetTpovIdFBUseCase @Inject constructor(private val repositoryFB: RepositoryFB) {
    operator fun invoke() = repositoryFB.getTpovIdFB()
}