package com.tpov.common.data.model.local

data class StructureCategoryData(
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
            "newCategoryPhoto" to newCategoryPhoto,
            "newSubCategoryPhoto" to newSubCategoryPhoto,
            "newSubsubCategoryPhoto" to newSubsubCategoryPhoto
        )
    }

    fun initCreateQuizActivity(newQuizEntity: QuizEntity, newCategoriesNames: Triple<String, String, String>, newEventId: Int)  =  StructureCategoryData(
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
