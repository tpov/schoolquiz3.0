package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import javax.inject.Inject

class ProfileUseCase @Inject constructor(private val repositoryProfile: RepositoryProfile) {

    fun getProfileFlow(tpovId: Int) = repositoryProfile.getProfileFlow(tpovId)

    fun getProfile(tpovId: Int) = repositoryProfile.getProfile(tpovId)

    fun getProfileList() = repositoryProfile.getProfileList()

    fun insertProfile(profile: ProfileEntity) {
        repositoryProfile.insertProfile(profile)
    }

    fun updateProfile(profile: ProfileEntity) {
        repositoryProfile.updateProfile(profile)
    }

    fun unloadProfile() {
        repositoryProfile.unloadProfile()
    }

    fun downloadProfile() {
        repositoryProfile.downloadProfile()
    }

}