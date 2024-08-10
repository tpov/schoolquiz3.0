package com.tpov.common.domain

import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.model.remote.StructureLocalData
import javax.inject.Inject

class StructureUseCase @Inject constructor(private val repositoryStuctureImpl: RepositoryStuctureImpl) {

    suspend fun fetchQuizzes() = repositoryStuctureImpl.fetchQuizzes()

    suspend fun pushStructureLocalData(ratingData: StructureLocalData) {
        repositoryStuctureImpl.pushStructureRating(ratingData)
    }

    suspend fun retryFailedLocalData() {
        repositoryStuctureImpl.retryFailedRatings()
    }

}