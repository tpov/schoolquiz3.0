package com.tpov.common.domain.model

import com.tpov.common.data.model.local.StructureInfoEntity
import com.tpov.common.presentation.model.PathStructure

data class SyncState(
    var eventId: Int,
    var structureCategoryDataListLocal: MutableList<StructureDataLocal> = mutableListOf(),
    var structureCategoryDataListRemote: MutableList<StructureDataLocal> = mutableListOf(),
    var changedListLocal: MutableList<ChangeVersionStructure> = mutableListOf(),
    var changedListRemote: MutableList<ChangeVersionStructure> = mutableListOf(),
    val structureInfoRemote: MutableList<StructureInfoEntity> = mutableListOf(),
    val structureInfoLocal: MutableList<StructureInfoEntity> = mutableListOf(),
    val allQuizRemote: MutableList<PathStructure> = mutableListOf(),
    val allQuizLocal: MutableList<PathStructure> = mutableListOf(),
    var currentStage: SyncStage = SyncStage.NOT_STARTED,
    var exception: String? = null
)