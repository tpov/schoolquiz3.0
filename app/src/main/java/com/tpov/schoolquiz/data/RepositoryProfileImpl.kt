package com.tpov.schoolquiz.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.data.core.FirebaseRequestInterceptor
import com.tpov.schoolquiz.data.database.ProfileDao
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.Profile
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class RepositoryProfileImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val firestore: FirebaseFirestore,
) : RepositoryProfile {
    override suspend fun getProfileFlow(): Flow<ProfileEntity?>? {
        return profileDao.getProfileFlow()
    }

    override suspend fun fetchProfile(tpovId: Int): Profile? {
        val profilesRef = firestore.collection("profiles")

        return try {
            val task = FirebaseRequestInterceptor.executeWithChecksSingleTask {
                profilesRef.document(tpovId.toString()).get()
            }.await()

            if (task.exists()) {
                val profileData = task.data
                Profile().fromHashMap(profileData!!)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("Firestore", "Error fetchProfile", e)
            null
        }
    }


    override suspend fun pushProfile(profile: Profile) {
        val profilesRef = firestore.collection("profiles")

        try {
            FirebaseRequestInterceptor.executeWithChecksSingleTask {
                profilesRef.document(profile.tpovId).set(profile.toHashMap())
            }.await()
        } catch (e: Exception) {
            Log.w("Firestore", "Error pushProfile", e)
        }
    }


    override suspend fun getProfile(): ProfileEntity {
        return profileDao.getProfile()
    }

    override suspend fun insertProfile(profile: ProfileEntity) {
profileDao.insertProfile(profile)
    }

    override suspend fun updateProfile(profile: ProfileEntity) {
        profileDao.updateProfiles(profile)
    }
}