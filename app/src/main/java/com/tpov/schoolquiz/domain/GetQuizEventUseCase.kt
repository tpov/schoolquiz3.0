package com.tpov.schoolquiz.domain

import android.content.Context
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import javax.inject.Inject

class GetQuizEventUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke() = repositoryDB.getQuizEvent()
}