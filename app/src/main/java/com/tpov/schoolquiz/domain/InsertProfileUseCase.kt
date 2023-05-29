package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class InsertProfileUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke(profileEntity: ProfileEntity) = repositoryDB.insertProfile(profileEntity)
}