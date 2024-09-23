package com.tpov.common.data.model.local

data class StructureData(
    val event: List<EventData> = emptyList()
)

data class EventData(
    val id: Int = 0,
    val category: List<CategoryData> = emptyList(),
    var isShowArchive: Boolean = false,
    var isShowDownload: Boolean = false
)

data class CategoryData(
    val id: Int = 0,
    val subcategory: List<SubCategoryData> = emptyList(),
    val nameQuiz: String = "",
    val dataUpdate: String =(System.currentTimeMillis() / 1000).toString(),
    val starsMaxLocal: Int = 0,
    val starsMaxRemote: Int = 0,
    val picture: String? = null,
    val ratingRemote: Int = 0,
    val ratingLocal: Int = 0,
    var isShowArchive: Boolean = false,
    var isShowDownload: Boolean = false
)

data class SubCategoryData(
    val id: Int = 0,
    val subSubcategory: List<SubsubCategoryData> = emptyList(),
    val nameQuiz: String = "",
    val dataUpdate: String = (System.currentTimeMillis() / 1000).toString(),
    val userName: String = "",
    val starsMaxLocal: Int = 0,
    val starsMaxRemote: Int = 0,
    val picture: String? = null,
    val ratingRemote: Int = 0,
    val ratingLocal: Int = 0,
    var isShowArchive: Boolean = false,
    var isShowDownload: Boolean = false
)

data class SubsubCategoryData(
    val id: Int = 0,
    val quizData: List<QuizData> = emptyList(),
    val nameQuiz: String = "",
    val dataUpdate: String = (System.currentTimeMillis() / 1000).toString(),
    val userName: String = "",
    val starsMaxLocal: Int = 0,
    val starsMaxRemote: Int = 0,
    val picture: String? = null,
    val ratingRemote: Int = 0,
    val ratingLocal: Int = 0,
    var isShowArchive: Boolean = false,
    var isShowDownload: Boolean = false
)

data class QuizData(
    val idQuiz: Int = 0,
    val nameQuiz: String = "",
    val dataUpdate: String = (System.currentTimeMillis() / 1000).toString(),
    val userName: String = "",
    val starsMaxLocal: Int = 0,
    val picture: String? = null,
    val starsMaxRemote: Int = 0,
    val ratingRemote: Int = 0,
    val ratingLocal: Int = 0,
    var isShowArchive: Boolean = false,
    var isShowDownload: Boolean = false,
    val tpovId: Int = 0
)
