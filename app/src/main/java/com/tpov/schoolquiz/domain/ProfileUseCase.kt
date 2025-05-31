package com.tpov.schoolquiz.domain

import android.util.Log
import com.tpov.common.data.model.ProfileStatus
import com.tpov.common.presentation.utils.DateUtil
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.ProfileRemote
import com.tpov.schoolquiz.data.fierbase.toProfile
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import javax.inject.Inject

class ProfileUseCase @Inject constructor(private val repositoryProfile: RepositoryProfile) {

    private val TAG = "ProfileUseCase"

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
        Log.d(TAG, "syncProfile: Starting sync")

        var newLocalProfile = if (repositoryProfile.getProfile() == null) {
            Log.d(TAG, "syncProfile: Condition - getProfile() is null")
            val newTpovId = repositoryProfile.getNewTpovId()
            ProfileEntity().create(newTpovId)

        } else if (repositoryProfile.getProfile()?.status == ProfileStatus.OFFLINE.statusCode) {
            Log.d(TAG, "syncProfile: Condition - status is OFFLINE")
            val newTpovId = repositoryProfile.getNewTpovId()

            val newProfile = ProfileEntity().copy(
                tpovId = newTpovId.tpovId,
                nickname = "User${newTpovId.tpovId}",
                status = ProfileStatus.ANONYMOUS.statusCode,
                authUid = newTpovId.uniqueHash
            )
            newProfile
        } else repositoryProfile.getProfile()!!

        Log.d(TAG, "syncProfile: Initial newLocalProfile tpovId = ${newLocalProfile.tpovId}")

        if (newLocalProfile.tpovId != null) {
            Log.d(TAG, "syncProfile: Attempting to fetch remote profile for tpovId = ${newLocalProfile.tpovId}")
            repositoryProfile.fetchProfile(newLocalProfile.tpovId!!)?.let { remoteProfile ->
                Log.d(TAG, "syncProfile: Successfully fetched remote profile")
                newLocalProfile = newLocalProfile.copy(
                    nickname = remoteProfile.basic.nickname,
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
                    datePremium = remoteProfile.dates.datePremium,
                    dateSynch = DateUtil().getDateQuiz()
                )
                Log.d(TAG, "syncProfile: Updated newLocalProfile with remote data")

            } ?: run {
                Log.d(TAG, "syncProfile: Failed to fetch remote profile (returned null)")
            }
            repositoryProfile.pushProfile(newLocalProfile.toProfile())
        } else {
            Log.d(TAG, "syncProfile: Cannot fetch remote profile - tpovId is null")
        }

        Log.d(TAG, "syncProfile: Updating local profile in database")
        repositoryProfile.insertProfile(newLocalProfile)
        Log.d(TAG, "syncProfile: Finished sync")
    }

}
