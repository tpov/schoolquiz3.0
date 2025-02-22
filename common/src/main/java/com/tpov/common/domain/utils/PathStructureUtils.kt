package com.tpov.common.domain.utils

import com.tpov.common.presentation.model.PathStructure

object PathStructureUtils {

    fun updatePath(path: PathStructure, nodeId: Int) {
        when {
            path.idCategory == -1 -> path.idCategory = nodeId
            path.idSubCategory == -1 -> path.idSubCategory = nodeId
            path.idSubsubCategory == -1 -> path.idSubsubCategory = nodeId
            else -> path.idQuiz = nodeId
        }
    }

    fun resetPath(path: PathStructure) {
        when {
            path.idQuiz != -1 -> path.idQuiz = -1
            path.idSubsubCategory != -1 -> path.idSubsubCategory = -1
            path.idSubCategory != -1 -> path.idSubCategory = -1
            path.idCategory != -1 -> path.idCategory = -1
        }
    }
}