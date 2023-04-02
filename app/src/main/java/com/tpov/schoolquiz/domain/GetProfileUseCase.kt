package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class GetProfileUseCase @Inject constructor(private val repository: RepositoryDB) {
    operator fun invoke(tpovId: Int) = repository.getProfile(tpovId)
}