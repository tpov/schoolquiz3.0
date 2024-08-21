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

    suspend fun syncData(): Boolean {
        // Логика синхронизации данных
        // Возвращает true, если синхронизация успешна и есть изменения
        // Возвращает false, если синхронизация успешна, но изменений нет
        return true // или false
    }
}