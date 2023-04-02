package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTpovIdByEmailUseCase @Inject constructor(private val repository: RepositoryDB) {
    operator fun invoke(email: String) = repository.getTpovIdByEmail(email)
}