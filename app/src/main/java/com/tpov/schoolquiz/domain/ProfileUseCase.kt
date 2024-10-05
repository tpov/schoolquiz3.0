package com.tpov.schoolquiz.domain

import com.tpov.common.data.core.Core.tpovId
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.toProfile
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import javax.inject.Inject

class ProfileUseCase @Inject constructor(private val repositoryProfile: RepositoryProfile) {

    suspend fun getProfileFlow() = repositoryProfile.getProfileFlow()

    suspend fun insertProfile(profile: ProfileEntity) {
        repositoryProfile.insertProfile(profile)
    }

    suspend fun updateProfile(profile: ProfileEntity) {
        repositoryProfile.updateProfile(profile)
    }

    suspend fun syncProfile() {
        val remoteProfile = repositoryProfile.fetchProfile(tpovId)!!
        val localProfile = repositoryProfile.getProfile()

        val newProfile = localProfile.copy(
            addMassage = remoteProfile.addPoints.addMassage,
            addPointsGold = remoteProfile.addPoints.addGold,
            addPointsNolics = remoteProfile.addPoints.addNolics,
            addPointsSkill = remoteProfile.addPoints.addSkill,
            addTrophy = remoteProfile.addPoints.addTrophy,

            admin = remoteProfile.qualification.admin,
            developer = remoteProfile.qualification.developer,
            sponsor = remoteProfile.qualification.sponsor,
            tester = remoteProfile.qualification.tester,
            moderator = remoteProfile.qualification.moderator,
            translater = remoteProfile.qualification.translater,

            dateBanned = remoteProfile.dates.dateBanned,
            datePremium = remoteProfile.dates.datePremium
        )

        repositoryProfile.pushProfile(newProfile.toProfile())
        repositoryProfile.updateProfile(newProfile)

    }

}