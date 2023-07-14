package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class GetTpovIdByEmailUseCase @Inject constructor(private val repository: RepositoryDB) {
    operator fun invoke(email: String) = repository.getTpovIdByEmail(email)
}