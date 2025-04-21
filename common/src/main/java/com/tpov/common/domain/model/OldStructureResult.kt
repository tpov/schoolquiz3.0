package com.tpov.common.domain.model

import com.tpov.common.presentation.model.PathStructure

data class OldStructureResult(
    val pathOld: PathStructure,
    val structureData: StructureDataLocal?
    )