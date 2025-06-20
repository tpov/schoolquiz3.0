package com.tpov.common.presentation

import com.tpov.common.presentation.model.PathStructure

interface NavigationProvider {
    fun openQuestionActivity(pathStructure: PathStructure, typeQuestion: Boolean)
}