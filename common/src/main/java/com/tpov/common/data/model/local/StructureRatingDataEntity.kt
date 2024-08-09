package com.tpov.common.data.model.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.model.remote.StructureRatingData

@Entity(tableName = "structure_rating_data")
data class StructureRatingDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val idEvent: Int,
    val idCategory: Int,
    val idSubCategory: Int,
    val idSubsubCategory: Int,
    val idQuiz: Int,
    var tpovId: Int,
    var rating: Int,
) {
    fun toStructureRatingData() = StructureRatingData(
        idEvent, idCategory, idSubCategory, idSubsubCategory, idQuiz, rating, tpovId
    )
}