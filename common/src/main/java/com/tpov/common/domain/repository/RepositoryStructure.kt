package com.tpov.common.domain.repository

import com.tpov.common.data.model.remote.StructureData
import com.tpov.common.data.model.remote.StructureLocalData

interface RepositoryStructure {
    suspend fun fetchQuizzes(): StructureData
    suspend fun pushStructureRating(ratingData: StructureLocalData)
}