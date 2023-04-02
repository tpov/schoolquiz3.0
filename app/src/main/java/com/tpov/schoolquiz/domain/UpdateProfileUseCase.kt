package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class UpdateProfileUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke(profileEntity: ProfileEntity) = repositoryDB.updateProfile(profileEntity)
}