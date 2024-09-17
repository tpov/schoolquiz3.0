package com.tpov.common.data.model.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject

/**
 *  Эту модель нужно отправить на файлстор вместе с обновленным квизом которая укажет какой квиз и куда нужно вставить по категориям и обновит StuctureData
 */
@Entity(tableName = "structure_category_data")
data class StructureCategoryDataEntity(
    @PrimaryKey(autoGenerate = true) var id: Int? = null,
    var oldEventId: Int = 0,
    var oldCategoryId: Int = 0,
    var oldSubCategoryId: Int = 0,
    var oldSubsubCategoryId: Int = 0,
    var oldIdQuizId: Int = 0,

    var newEventId: Int = 0,
    var newCategoryName: String = "",
    var newSubCategoryName: String = "",
    var newSubsubCategoryName: String = "",
    var newQuizName: String = "",

    var newCategoryPhoto: String = "",
    var newSubCategoryPhoto: String = "",
    var newSubsubCategoryPhoto: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "oldEventId" to oldEventId,
            "oldCategoryId" to oldCategoryId,
            "oldSubCategoryId" to oldSubCategoryId,
            "oldSubsubCategoryId" to oldSubsubCategoryId,
            "oldIdQuizId" to oldIdQuizId,

            "newEventId" to newEventId,
            "newCategoryName" to newCategoryName,
            "newSubCategoryName" to newSubCategoryName,
            "newSubsubCategoryName" to newSubsubCategoryName,
            "newQuizName" to newQuizName,

            "newCategoryPhoto" to newCategoryPhoto,
            "newSubCategoryPhoto" to newSubCategoryPhoto,
            "newSubsubCategoryPhoto" to newSubsubCategoryPhoto
        )
    }

    fun initCreateQuizActivity(
        newQuizEntity: QuizEntity,
        newCategoriesNames: Triple<String, String, String>,
        newEventId: Int,

        newCategoryPhoto: String,
        newSubCategoryPhoto: String,
        newSubsubCategoryPhoto: String,
        newQuizName: String
    ) = StructureCategoryDataEntity(
        oldEventId = newQuizEntity.event,
        oldCategoryId = newQuizEntity.idCategory,
        oldSubCategoryId = newQuizEntity.idSubcategory,
        oldSubsubCategoryId = newQuizEntity.idSubsubcategory,
        oldIdQuizId = newQuizEntity.id ?: 0,

        newEventId = newEventId,
        newCategoryName = newCategoriesNames.first,
        newSubCategoryName = newCategoriesNames.second,
        newSubsubCategoryName = newCategoriesNames.third,
        newCategoryPhoto = newCategoryPhoto,
        newSubCategoryPhoto = newSubCategoryPhoto,
        newSubsubCategoryPhoto = newSubsubCategoryPhoto,
        newQuizName = newQuizName
    )
}

fun fromJson(json: JSONObject): StructureCategoryDataEntity {
    return StructureCategoryDataEntity(
        id = if (json.has("id")) json.getInt("id") else null,
        oldEventId = if (json.has("oldEventId")) json.getInt("oldEventId") else 0,
        oldCategoryId = if (json.has("oldCategoryId")) json.getInt("oldCategoryId") else 0,
        oldSubCategoryId = if (json.has("oldSubCategoryId")) json.getInt("oldSubCategoryId") else 0,
        oldSubsubCategoryId = if (json.has("oldSubsubCategoryId")) json.getInt("oldSubsubCategoryId") else 0,
        oldIdQuizId = if (json.has("oldIdQuizId")) json.getInt("oldIdQuizId") else 0,

        newEventId = if (json.has("newEventId")) json.getInt("newEventId") else 0,
        newCategoryName = if (json.has("newCategoryName")) json.getString("newCategoryName") else "",
        newSubCategoryName = if (json.has("newSubCategoryName")) json.getString("newSubCategoryName") else "",
        newSubsubCategoryName = if (json.has("newSubsubCategoryName")) json.getString("newSubsubCategoryName") else "",
        newQuizName = if (json.has("newQuizName")) json.getString("newQuizName") else "",

        newCategoryPhoto = if (json.has("newCategoryPhoto")) json.getString("newCategoryPhoto") else "",
        newSubCategoryPhoto = if (json.has("newSubCategoryPhoto")) json.getString("newSubCategoryPhoto") else "",
        newSubsubCategoryPhoto = if (json.has("newSubsubCategoryPhoto")) json.getString("newSubsubCategoryPhoto") else ""
    )
}
