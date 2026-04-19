package com.tpov.common.domain.utils

import com.tpov.common.presentation.model.PathStructure

object PathStructureUtils {

    fun updatePath(path: PathStructure, nameItem: String) {
        when {
            path.nameCategory == "" -> path.nameCategory = nameItem
            path.nameSubCategory == "" -> path.nameSubCategory = nameItem
            path.nameSubsubCategory == "" -> path.nameSubsubCategory = nameItem
            else -> path.nameQuiz = nameItem
        }
    }

    fun resetPath(path: PathStructure) {
        when {
            path.nameQuiz != "" -> path.nameQuiz = ""
            path.nameSubsubCategory != "" -> path.nameSubsubCategory = ""
            path.nameSubCategory != "" -> path.nameSubCategory = ""
            path.nameCategory != "" -> path.nameCategory = ""
        }
    }
}
