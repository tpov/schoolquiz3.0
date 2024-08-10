package com.tpov.common.data.model.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.model.remote.StructureLocalData

@Entity(tableName = "structure_rating_data")
data class StructureRatingDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val idEvent: Int,
    val idCategory: Int,
    val idSubCategory: Int,
    val idSubsubCategory: Int,
    val idQuiz: Int,
    var tpovId: Int,
    val starsMaxLocal: Int,
    val ratingLocal: Int,
) {
    fun toStructureRatingData() = StructureLocalData(
        idEvent = idEvent,
        idCategory = idCategory,
        idSubCategory = idSubCategory,
        idSubsubCategory = idSubsubCategory,
        idQuiz = idQuiz,
        tpovId = tpovId,
        starsMaxLocal = starsMaxLocal,
        ratingLocal = ratingLocal
    )
}