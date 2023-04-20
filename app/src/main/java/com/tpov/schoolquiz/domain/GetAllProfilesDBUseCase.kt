package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryDB
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class GetAllProfilesDBUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke() = repositoryDB.getAllProfiles()
}