package com.tpov.schoolquiz.domain

import android.content.Context
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class GetProfileFlowUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {
    operator fun invoke(tpovId: Int) = repositoryDB.getProfileFlow(tpovId)
}