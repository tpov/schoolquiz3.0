package com.tpov.common.data.model.remote

import com.tpov.common.data.model.local.StructureRatingDataEntity

data class StructureRatingData(
    val idEvent: Int,
    val idCategory: Int,
    val idSubCategory: Int,
    val idSubsubCategory: Int,
    val idQuiz: Int,
    var tpovId: Int,
    var starsMaxLocal: Int,
    val ratingLocal: Int,
) {
    fun toMap(): Map<String, Any> {
        val dataMap = mutableMapOf<String, Any>()

        if (idEvent != 0) dataMap["idEvent"] = idEvent
        if (idCategory != 0) dataMap["idCategory"] = idCategory
        if (idSubCategory != 0) dataMap["idSubCategory"] = idSubCategory
        if (idSubsubCategory != 0) dataMap["idSubsubCategory"] = idSubsubCategory
        if (idQuiz != 0) dataMap["idQuiz"] = idQuiz
        if (tpovId != 0) dataMap["tpovId"] = tpovId
        if (starsMaxLocal != 0) dataMap["starsMaxLocal"] = starsMaxLocal
        if (ratingLocal != 0) dataMap["ratingLocal"] = ratingLocal

        return dataMap
    }

    fun toStructureRatingDataEntity() = StructureRatingDataEntity(
        id = 0,
        idEvent = idEvent,
        idCategory = idCategory,
        idSubCategory = idSubCategory,
        idSubsubCategory = idSubsubCategory,
        idQuiz = idQuiz,
        ratingLocal = ratingLocal,
        tpovId = tpovId,
        starsMaxLocal = starsMaxLocal
    )
}
