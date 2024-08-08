package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.remote.StructureData
import com.tpov.common.data.model.remote.StructureRatingData

interface RepositoryStructure {
    suspend fun fetchQuizzes(): StructureData
    suspend fun getQuizzes(): StructureData
    suspend fun saveStructureRating(ratingData: StructureRatingData)
    suspend fun pushQuiz(quiz: StructureData)
    suspend fun insertQuiz(quiz: StructureData)
    suspend fun deleteQuizById(idQuiz: Int)
    suspend fun deleteRemoteQuizById(quiz: QuizEntity)
}