package com.tpov.common.domain.usecase

import com.tpov.common.data.RepositoryStructureImpl
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.data.model.remote.StructureEditData
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.presentation.model.PathStructure
import javax.inject.Inject

class StructureUseCase @Inject constructor(private val repositoryStructureImpl: RepositoryStructureImpl) {

    suspend fun fetchStructureCategoryDataList(event: EventQuiz) =
        repositoryStructureImpl.fetchStructureCategoryDataList(event)

    suspend fun getStructureEventData(event: EventQuiz) = repositoryStructureImpl.getStructureEventData(event.name)

    suspend fun fetchStructureInfo(pathStructure: PathStructure) =
        repositoryStructureImpl.fetchStructureInfo(pathStructure)

    suspend fun fetchStructureInfoData(path: PathStructure) =
        repositoryStructureImpl.fetchStructureInfoData(path)

    suspend fun insertEditStructure(structureEditData: StructureEditData) {
        repositoryStructureImpl.insertEditStructure(structureEditData)
    }

    suspend fun getEditStructure() = repositoryStructureImpl.getEditStructure()

    suspend fun pushEditStructure(structureEditData: StructureEditData) {
        repositoryStructureImpl.pushEditStructure(structureEditData)
    }

    suspend fun clearStructureEdit() {
        repositoryStructureImpl.clearStructureEdit()
    }

    suspend fun insertStructureData(structureDataLocal: StructureDataLocal, event: EventQuiz) {
        // todo when edit database schema

        repositoryStructureImpl.insertStructureData(structureDataLocal.toStructureDataEntity()!!, event.name)
    }

    suspend fun updateStructureDataList(structureDataLocal: List<StructureDataLocal>, event: EventQuiz) {
        // todo when edit database schema

        repositoryStructureImpl.saveStructureData(structureDataLocal, event.name)
    }

    fun fetchPictureStructure(pictureName: String) {
        repositoryStructureImpl.fetchPictureStructure(pictureName)
    }


}
