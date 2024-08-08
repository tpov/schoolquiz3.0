package com.tpov.common.data.model.remote

data class StructureRatingData(
    val idEvent: Int,
    val idCategory: Int,
    val idSubCategory: Int,
    val idSubsubCategory: Int,
    val idQuiz: Int,
    var rating: Int,
    var tpovId: Int
)

fun StructureRatingData.toMap(): Map<String, Any> {
    val dataMap = mutableMapOf<String, Any>(
        "idEvent" to this.idEvent,
        "idCategory" to this.idCategory,
        "idQuiz" to this.idQuiz,
        "rating" to this.rating,
        "tpovId" to this.tpovId
    )

    if (this.idSubCategory != 0) {
        dataMap["idSubCategory"] = this.idSubCategory
    }

    if (this.idSubsubCategory != 0) {
        dataMap["idSubsubCategory"] = this.idSubsubCategory
    }

    return dataMap
}