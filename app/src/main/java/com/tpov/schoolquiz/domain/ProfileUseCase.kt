package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.ProfileRemote
import com.tpov.schoolquiz.data.fierbase.toProfile
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import javax.inject.Inject

class ProfileUseCase @Inject constructor(private val repositoryProfile: RepositoryProfile) {

    suspend fun getProfileFlow() = repositoryProfile.getProfileFlow()

    suspend fun insertAndPushProfile(profile: ProfileEntity) {
        repositoryProfile.insertProfile(profile)
        repositoryProfile.pushProfile(profile.toProfile())
    }

    suspend fun updateProfile(profile: ProfileEntity) {
        repositoryProfile.updateProfile(profile)
    }

    suspend fun pushProfile(profileRemote: ProfileRemote) {
        repositoryProfile.pushProfile(profileRemote)
    }

    suspend fun syncProfile() {
        val localProfile = repositoryProfile.getProfile()
        val remoteProfile = repositoryProfile.fetchProfile(localProfile.tpovId) ?: localProfile.toProfile()

        val newProfile = localProfile.copy(
            addMassage = remoteProfile.addPoints.addMassage,
            addPointsGold = remoteProfile.addPoints.addGold.toInt(),
            addPointsNolics = remoteProfile.addPoints.addNolics.toInt(),
            addPointsSkill = remoteProfile.addPoints.addSkill.toInt(),
            addTrophy = remoteProfile.addPoints.addTrophy,

            admin = remoteProfile.qualification.admin.toInt(),
            developer = remoteProfile.qualification.developer.toInt(),
            sponsor = remoteProfile.qualification.sponsor.toInt(),
            tester = remoteProfile.qualification.tester.toInt(),
            moderator = remoteProfile.qualification.moderator.toInt(),
            translater = remoteProfile.qualification.translater.toInt(),

            dateBanned = remoteProfile.dates.dateBanned,
            datePremium = remoteProfile.dates.datePremium
        )

        repositoryProfile.pushProfile(newProfile.toProfile())
        repositoryProfile.updateProfile(newProfile)


    }
}
