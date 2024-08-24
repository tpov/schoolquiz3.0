package com.tpov.common.domain.repository

import com.tpov.common.data.model.remote.StructureData
import com.tpov.common.data.model.remote.StructureLocalData

interface RepositoryStructure {
    suspend fun fetchStructureData(): StructureData
    suspend fun pushStructureRating(ratingData: StructureLocalData)
    suspend fun loadStructureData(): com.tpov.common.data.model.local.StructureData?
    suspend fun saveStructureData(structureData: com.tpov.common.data.model.local.StructureData)

    suspend fun saveListUpdateQuiz(list: List<String>)
    suspend fun loadListUpdateQuiz(): List<String>
}