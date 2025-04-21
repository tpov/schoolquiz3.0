package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.StructureDataEntity
import com.tpov.common.data.model.local.StructureInfoEntity
import com.tpov.common.data.model.local.UpdatedStructureData
import com.tpov.common.data.model.remote.StructureEditData
import com.tpov.common.data.model.remote.StructureInfoRemote
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.presentation.model.PathStructure

interface RepositoryStructure {
    suspend fun fetchStructureCategoryDataList(eventId: Int): List<StructureDataLocal>?

    suspend fun pushStructureData(
        structureDataLocal: StructureDataLocal, categoryNumber: Int)

    suspend fun fetchStructureInfoData(path: PathStructure): StructureInfoRemote?
    suspend fun getStructureEventData(eventId: Int, vararg path: Int): List<StructureDataLocal>
    fun fetchPictureStructure(path: String)

    suspend fun saveStructureData(structureDataCategoryList: List<StructureDataLocal>,
                                  eventId: Int)
    suspend fun insertEditStructure(structureEditData: StructureEditData)
    suspend fun pushEditStructure(structureEditData: StructureEditData)
    suspend fun getEditStructure(): List<StructureEditData>

    fun deleteLocalPictureStructure(updatedStructureData: UpdatedStructureData) // This is StructureEdit?
    fun fetchPictureStructure(updatedStructureData: UpdatedStructureData)
    suspend fun fetchStructureInfo(path: PathStructure): StructureInfoEntity?
    suspend fun pushStructureInfoData(ratingData: StructureInfoRemote, path: PathStructure)
    suspend fun updateStructureData(structureDataEntity: StructureDataEntity, eventId: Int)
}