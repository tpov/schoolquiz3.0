package com.tpov.common.domain.model

import com.tpov.common.presentation.model.PathStructure

data class ChangeVersionStructure(
    val name: String,
    val pathStructure: PathStructure
)
