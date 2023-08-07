package com.tpov.schoolquiz.domain

import androidx.lifecycle.LiveData
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class GetQuizLiveDataUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    suspend fun getQuizUseCase(tpovId: Int): LiveData<List<QuizEntity>> {

        return repositoryDB.getQuizLiveData(tpovId)
    }
}