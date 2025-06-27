package com.tpov.schoolquiz.domain

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.tpov.common.data.model.ProfileStatus
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.ProfileRemote
import com.tpov.schoolquiz.data.fierbase.toProfile
import com.tpov.schoolquiz.domain.ProfileExtention.createAnonymousProfile
import com.tpov.schoolquiz.domain.ProfileExtention.updateFromRemote
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
        val currentProfile = repositoryProfile.getProfile()
        val isAuthenticated = FirebaseAuth.getInstance().currentUser?.uid != null && repositoryProfile.getProfile()?.tpovId != null
        val isOffline = currentProfile?.status == ProfileStatus.OFFLINE.statusCode

        var newLocalProfile = when {
            currentProfile == null -> ProfileEntity().create(repositoryProfile.getNewTpovId())
            isOffline || !isAuthenticated -> {
                val newTpovId = repositoryProfile.getNewTpovId()
                if (newTpovId != null) currentProfile.createAnonymousProfile(newTpovId)
                else currentProfile
            }
            else -> currentProfile
        }

        if (isAuthenticated) {
            Log.d("gdrfgdfrg", "isAuthenticated")
            repositoryProfile.fetchProfile(newLocalProfile.tpovId ?: 0)?.let { remoteProfile ->
                Log.d("gdrfgdfrg", "isAuthenticated")
                newLocalProfile = newLocalProfile.updateFromRemote(remoteProfile)
            }

            repositoryProfile.pushProfile(newLocalProfile.toProfile())
        }

        repositoryProfile.insertProfile(newLocalProfile)

    }
}
