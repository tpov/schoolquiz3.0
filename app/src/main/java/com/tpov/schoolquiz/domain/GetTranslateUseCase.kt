package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class GetTranslateUseCase @Inject constructor(private val repositoryFB: RepositoryFB) {
    operator fun invoke() = repositoryFB.getTranslateFB()
}