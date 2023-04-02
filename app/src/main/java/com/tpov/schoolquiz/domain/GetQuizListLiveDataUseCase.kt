package com.tpov.schoolquiz.domain

import android.util.Log
import androidx.lifecycle.LiveData
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetQuizLiveDataUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    fun getQuizUseCase(tpovId: Int): LiveData<List<QuizEntity>> {

        return repositoryDB.getQuizLiveData(tpovId)
    }
}