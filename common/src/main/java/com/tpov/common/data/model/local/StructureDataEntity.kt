package com.tpov.common.data.model.local

data class StructureData(
    val event: List<EventData>,
)

data class EventData(
    val id: Int,
    val category: List<CategoryData>,
    val isShowArchive: Boolean,
    val isShowDownload: Boolean
)

data class CategoryData(
    val id: Int,
    val subcategory: List<SubCategoryData>,

    val nameQuiz: String,
    val dataUpdate: String,
    val starsMaxLocal: Int,
    val starsMaxRemote: Int,
    val versionQuiz: Int,
    val picture: String?,
    val ratingRemote: Int,
    val ratingLocal: Int,
    val isShowArchive: Boolean,
    val isShowDownload: Boolean
)

data class SubCategoryData(
    val id: Int,
    val subSubcategory: List<SubsubCategoryData>,

    val nameQuiz: String,
    val dataUpdate: String,
    val userName: String,
    val starsMaxLocal: Int,
    val starsMaxRemote: Int,
    val versionQuiz: Int,
    val picture: String?,
    val ratingRemote: Int,
    val ratingLocal: Int,
    val isShowArchive: Boolean,
    val isShowDownload: Boolean
)

data class SubsubCategoryData(
    val id: Int,
    val quizData: List<QuizData>,

    val nameQuiz: String,
    val dataUpdate: String,
    val userName: String,
    val starsMaxLocal: Int,
    val starsMaxRemote: Int,
    val versionQuiz: Int,
    val picture: String?,
    val ratingRemote: Int,
    val ratingLocal: Int,
    val isShowArchive: Boolean,
    val isShowDownload: Boolean
)

data class QuizData(
    val idQuiz: Int,
    val quizEntity: QuizEntity?,
    val nameQuiz: String,
    val ratingRemote: Int,
    val isShowArchive: Boolean,
    val isShowDownload: Boolean
)