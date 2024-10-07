package com.tpov.schoolquiz.domain

import android.util.Log
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.Profile
import com.tpov.schoolquiz.data.fierbase.toProfile
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import javax.inject.Inject

class ProfileUseCase @Inject constructor(private val repositoryProfile: RepositoryProfile) {

    suspend fun getProfileFlow() = repositoryProfile.getProfileFlow()

    suspend fun insertAndPushProfile(profile: ProfileEntity) {
        Log.d("qweqwe", "1 $profile")
        repositoryProfile.insertProfile(profile)
        repositoryProfile.pushProfile(profile.toProfile())
    }

    suspend fun updateProfile(profile: ProfileEntity) {
        repositoryProfile.updateProfile(profile)
    }

    suspend fun pushProfile(profile: Profile) {
        repositoryProfile.pushProfile(profile)
    }

    suspend fun syncProfile() {
        Log.d("qweqwe", "syncProfile")
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