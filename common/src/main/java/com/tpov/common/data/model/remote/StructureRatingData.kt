package com.tpov.common.data.model.remote

import com.tpov.common.data.model.local.StructureRatingDataEntity

data class StructureRatingData(
    val idEvent: Int,
    val idCategory: Int,
    val idSubCategory: Int,
    val idSubsubCategory: Int,
    val idQuiz: Int,
    var rating: Int,
    var tpovId: Int
) {
    fun toMap(): Map<String, Any> {
        val dataMap = mutableMapOf<String, Any>()

        if (idEvent != 0) dataMap["idEvent"] = idEvent
        if (idCategory != 0) dataMap["idCategory"] = idCategory
        if (idSubCategory != 0) dataMap["idSubCategory"] = idSubCategory
        if (idSubsubCategory != 0) dataMap["idSubsubCategory"] = idSubsubCategory
        if (idQuiz != 0) dataMap["idQuiz"] = idQuiz
        if (rating != 0) dataMap["rating"] = rating
        if (tpovId != 0) dataMap["tpovId"] = tpovId

        return dataMap
    }

    fun toStructureRatingDataEntity() = StructureRatingDataEntity(
        0, idEvent, idCategory, idSubCategory, idSubsubCategory, idQuiz, rating, tpovId
    )
}
