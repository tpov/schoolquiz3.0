package com.tpov.common.data.model.local

data class StructureData(
    val event: List<EventData> = emptyList()
)

data class EventData(
    val id: Int = 0,
    val category: List<CategoryData> = emptyList(),
    var isShowArchive: Boolean = true,
    var isShowDownload: Boolean = true
)

data class CategoryData(
    val id: Int = 0,
    val subcategory: List<SubCategoryData> = emptyList(),
    val nameQuiz: String = "",
    val dataUpdate: String = (System.currentTimeMillis() / 1000).toString(),
    val starsMaxLocal: Int = 0,
    val starsMaxRemote: Int = 0,
    val picture: String = "",
    val ratingRemote: Int = 0,
    val ratingLocal: Int = 0,
    var isShowArchive: Boolean = true,
    var isShowDownload: Boolean = true
)

data class SubCategoryData(
    val id: Int = 0,
    val subSubcategory: List<SubsubCategoryData> = emptyList(),
    val nameQuiz: String = "",
    val dataUpdate: String = (System.currentTimeMillis() / 1000).toString(),
    val userName: String = "",
    val starsMaxLocal: Int = 0,
    val starsMaxRemote: Int = 0,
    val picture: String = "",
    val ratingRemote: Int = 0,
    val ratingLocal: Int = 0,
    var isShowArchive: Boolean = true,
    var isShowDownload: Boolean = true
) {
    fun toFlattenedQuizData() = FlattenedQuizData(
        this.id,
        this.nameQuiz,
        this.dataUpdate,
        this.userName,
        this.starsMaxLocal,
        this.starsMaxRemote,
        this.picture,
        this.ratingRemote,
        this.ratingLocal,
        this.isShowArchive,
        this.isShowDownload
    )
}

data class SubsubCategoryData(
    val id: Int = 0,
    val quizData: List<QuizData> = emptyList(),
    val nameQuiz: String = "",
    val dataUpdate: String = (System.currentTimeMillis() / 1000).toString(),
    val userName: String = "",
    val starsMaxLocal: Int = 0,
    val starsMaxRemote: Int = 0,
    val picture: String = "",
    val ratingRemote: Int = 0,
    val ratingLocal: Int = 0,
    var isShowArchive: Boolean = true,
    var isShowDownload: Boolean = true
)

data class QuizData(
    val idQuiz: Int = 0,
    val nameQuiz: String = "",
    val dataUpdate: String = (System.currentTimeMillis() / 1000).toString(),
    val userName: String = "",
    val starsMaxLocal: Int = 0,
    val picture: String = "",
    val starsMaxRemote: Int = 0,
    val ratingRemote: Int = 0,
    val ratingLocal: Int = 0,
    var isShowArchive: Boolean = true,
    var isShowDownload: Boolean = true,
    val tpovId: Int = 0
) {
    fun toFlattenedQuizData() = FlattenedQuizData(
        this.idQuiz,
        this.nameQuiz,
        this.dataUpdate,
        this.userName,
        this.starsMaxLocal,
        this.starsMaxRemote,
        this.picture,
        this.ratingRemote,
        this.ratingLocal,
        this.isShowArchive,
        this.isShowDownload
    )
}

data class FlattenedQuizData(
    val id: Int,
    val name: String,
    val dataUpdate: String,
    val userName: String,
    val starsMaxLocal: Int,
    val starsMaxRemote: Int,
    val picture: String,
    val ratingRemote: Int,
    val ratingLocal: Int,
    val isShowArchive: Boolean,
    val isShowDownload: Boolean
)
