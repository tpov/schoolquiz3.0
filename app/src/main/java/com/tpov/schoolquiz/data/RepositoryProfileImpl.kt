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
    override fun getProfileFlow(tpovId: Int): Flow<ProfileEntity> {
        TODO("Not yet implemented")
    }

    override fun getProfile(tpovId: Int): ProfileEntity {
        TODO("Not yet implemented")
    }

    override fun getProfileList(): List<ProfileEntity> {
        TODO("Not yet implemented")
    }

    override fun insertProfile(profile: ProfileEntity) {
        TODO("Not yet implemented")
    }

    override fun updateProfile(profile: ProfileEntity) {
        TODO("Not yet implemented")
    }

    override fun unloadProfile() {
        TODO("Not yet implemented")
    }

    override fun downloadProfile() {
        TODO("Not yet implemented")
    }

}