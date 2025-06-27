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

    suspend fun insertEditStructure(structureEditData: StructureEditData) {
        repositoryStructureImpl.insertEditStructure(structureEditData)
    }

    suspend fun getEditStructure() = repositoryStructureImpl.getEditStructure()
    suspend fun pushEditStructure(structureEditData: StructureEditData) =
        repositoryStructureImpl.pushEditStructure(structureEditData)

    suspend fun clearStructureEdit() {
        repositoryStructureImpl.clearStructureEdit()
    }

    suspend fun insertStructureData(structureDataLocal: StructureDataLocal, event: EventQuiz) {

        StructureDataLocal( children = mutableListOf( structureDataLocal)).printFullStructure("drl;gklpsdre")
        repositoryStructureImpl.insertStructureData(structureDataLocal.toStructureDataEntity()!!, event.name)
    }

    suspend fun updateStructureDataList(structureDataLocal: List<StructureDataLocal>, event: EventQuiz) {
        android.util.Log.d("StructureUseCase", "💾 Saving structure data for ${event.name}: ${structureDataLocal.size} categories")
        // Сохраняем всю структуру как единое дерево
        repositoryStructureImpl.saveStructureData(structureDataLocal, event.name)
        android.util.Log.d("StructureUseCase", "✅ Structure data saved successfully")
    }
}
