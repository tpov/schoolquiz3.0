package com.tpov.common.domain.repository

import com.tpov.common.data.model.entity.StructureDataEntity
import com.tpov.common.data.model.entity.StructureInfoEntity
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.data.model.remote.StructureEditData
import com.tpov.common.data.model.remote.StructureInfoRemote
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.presentation.model.PathStructure

interface RepositoryStructure {
    suspend fun fetchStructureCategoryDataList(eventQuiz: EventQuiz): List<StructureDataLocal>?

    suspend fun pushStructureData(structureDataLocal: StructureDataLocal, category: String)

    suspend fun fetchStructureInfoData(path: PathStructure): StructureInfoRemote?
    suspend fun getStructureEventData(event: String, vararg path: String): List<StructureDataLocal>?
    fun fetchPictureStructure(namePicture: String)
    fun clearStructureEdit()

    suspend fun saveStructureData(structureDataCategoryList: List<StructureDataLocal>,
                                  event: String)
    suspend fun insertEditStructure(structureEditData: StructureEditData)
    suspend fun pushEditStructure(structureEditData: StructureEditData)
    suspend fun getEditStructure(): List<StructureEditData>

    fun deleteLocalPictureStructure(namePicture: String)
    suspend fun fetchStructureInfo(path: PathStructure): StructureInfoEntity?
    suspend fun pushStructureInfoData(ratingData: StructureInfoRemote, path: PathStructure)
    suspend fun insertStructureData(structureDataEntity: StructureDataEntity, event: String)
}
