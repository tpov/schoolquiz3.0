package com.tpov.common.data.model.local

data class StructureData(
    val event: List<EventData>,
) {

}

data class EventData(
    val id: Int,
    val category: List<CategoryData>,
    var isShowArchive: Boolean,
    var isShowDownload: Boolean
) {

}

data class CategoryData(
    val id: Int,
    val subcategory: List<SubCategoryData>,
    val nameQuiz: String,
    val dataUpdate: String,
    val starsMaxLocal: Int,
    val starsMaxRemote: Int,
    val picture: String?,
    val ratingRemote: Int,
    val ratingLocal: Int,
    var isShowArchive: Boolean,
    var isShowDownload: Boolean
) {

}

data class SubCategoryData(
    val id: Int,
    val subSubcategory: List<SubsubCategoryData>,
    val nameQuiz: String,
    val dataUpdate: String,
    val userName: String,
    val starsMaxLocal: Int,
    val starsMaxRemote: Int,
    val picture: String?,
    val ratingRemote: Int,
    val ratingLocal: Int,
    var isShowArchive: Boolean,
    var isShowDownload: Boolean
) {

}

data class SubsubCategoryData(
    val id: Int,
    val quizData: List<QuizData>,
    val nameQuiz: String,
    val dataUpdate: String,
    val userName: String,
    val starsMaxLocal: Int,
    val starsMaxRemote: Int,
    val picture: String?,
    val ratingRemote: Int,
    val ratingLocal: Int,
    var isShowArchive: Boolean,
    var isShowDownload: Boolean
) {

}

data class QuizData(
    val idQuiz: Int,
    val nameQuiz: String,
    val dataUpdate: String,
    val userName: String,
    val starsMaxLocal: Int,
    val picture: String?,
    val starsMaxRemote: Int,
    val ratingRemote: Int,
    val ratingLocal: Int,
    var isShowArchive: Boolean,
    var isShowDownload: Boolean,
    val tpovId: Int
) {

}
