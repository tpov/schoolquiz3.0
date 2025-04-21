package com.tpov.common.domain.model

import com.tpov.common.data.model.local.StructureInfoEntity
import com.tpov.common.data.model.remote.StructureEditData
import com.tpov.common.presentation.model.PathStructure

data class SyncState(
    var eventId: Int,
    var structureCategoryDataListLocal: MutableList<StructureDataLocal> = mutableListOf(),
    var structureCategoryDataListRemote: MutableList<StructureDataLocal> = mutableListOf(),
    var changedListQuestionLocal: MutableList<ChangeVersionStructure> = mutableListOf(),
    var changeStructureInfoListLocal: MutableList<StructureInfoEntity> = mutableListOf(),
    var changedListQuestionRemote: MutableList<ChangeVersionStructure> = mutableListOf(),
    val structureInfoGlobal: MutableList<StructureInfoEntity> = mutableListOf(),
    val structureInfoLocal: MutableList<StructureInfoEntity> = mutableListOf(),
    val allQuizRemote: MutableList<PathStructure> = mutableListOf(),
    val allQuizLocal: MutableList<PathStructure> = mutableListOf(),
    var currentStage: SyncStage = SyncStage.NOT_STARTED,
    var editIdsList: MutableList<StructureEditData> = mutableListOf(),
    var exception: String? = null
)