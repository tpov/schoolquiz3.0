package com.tpov.schoolquiz.data

import com.tpov.common.data.database.QuizDao
import com.tpov.schoolquiz.data.database.ProfileDao
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RepositoryProfileImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val quizDao: QuizDao
) : RepositoryProfile {
    override fun getProfileFlow(tpovId: Int): Flow<ProfileEntity?>? {
        return null
    }

    override fun getProfile(tpovId: Int): ProfileEntity? {
        return null
    }

    override fun getProfileList(): List<ProfileEntity?>? {

        return null
    }

    override fun insertProfile(profile: ProfileEntity) {

    }

    override fun updateProfile(profile: ProfileEntity) {

    }

    override fun unloadProfile() {

    }

    override fun downloadProfile() {

    }

}