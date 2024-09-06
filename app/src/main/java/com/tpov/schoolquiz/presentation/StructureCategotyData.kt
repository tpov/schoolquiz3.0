package com.tpov.schoolquiz.presentation

import com.tpov.common.data.model.local.QuizEntity

data class StructureCategotyData(
    var oldEventId: Int = 0,
    var oldCategoryId: Int = 0,
    var oldSubCategoryId: Int = 0,
    var oldSubsubCategoryId: Int = 0,
    var oldIdQuizId: Int = 0,

    var newEventId: Int = 0,
    var newCategoryName: String = "",
    var newSubCategoryName: String = "",
    var newSubsubCategoryName: String = "",

    var newCategoryPhoto: String = "",
    var newSubCategoryPhoto: String = "",
    var newSubsubCategoryPhoto: String = ""
) {
    fun init(newQuizEntity: QuizEntity, newCategoriesNames: Triple<String, String, String>, newEventId: Int)  =  StructureCategotyData(
        oldEventId = newQuizEntity.event,
        oldCategoryId = newQuizEntity.idCategory,
        oldSubCategoryId = newQuizEntity.idSubcategory,
        oldSubsubCategoryId = newQuizEntity.idSubsubcategory,
        oldIdQuizId = newQuizEntity.id ?: 0,

        newEventId = newEventId,
        newCategoryName = newCategoriesNames.first,
        newSubCategoryName = newCategoriesNames.second,
        newSubsubCategoryName = newCategoriesNames.third,
    )
}
