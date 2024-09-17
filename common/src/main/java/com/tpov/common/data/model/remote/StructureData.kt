package com.tpov.common.data.model.remote

data class StructureData(
    val event: List<EventData> = emptyList()
) {
    // Конструктор по умолчанию для Firestore
    constructor() : this(emptyList())

    fun toStructureDataLocal() = com.tpov.common.data.model.local.StructureData(
        event = event.map { it.toEventDataLocal() }
    )
}

data class EventData(
    val id: Int = 0,
    val category: List<CategoryData> = emptyList(),
) {
    // Конструктор по умолчанию для Firestore
    constructor() : this(0, emptyList())

    fun toEventDataLocal(): com.tpov.common.data.model.local.EventData {
        return com.tpov.common.data.model.local.EventData(
            id = id,
            category = category.map { it.toCategoryDataLocal() },
            isShowArchive = false,
            isShowDownload = false
        )
    }
}

data class CategoryData(
    val id: Int = 0,
    val subcategory: List<SubCategoryData> = emptyList(),
    val nameQuiz: String = "",
    val dataUpdate: String = "",
    val starsMaxRemote: Int = 0,
    val picture: String? = null,
    val ratingRemote: Int = 0
) {
    // Конструктор по умолчанию для Firestore
    constructor() : this(0, emptyList(), "", "", 0, null, 0)

    fun toCategoryDataLocal(): com.tpov.common.data.model.local.CategoryData {
        return com.tpov.common.data.model.local.CategoryData(
            id = id,
            subcategory = subcategory.map { it.toSubCategoryDataLocal() },
            nameQuiz = nameQuiz,
            dataUpdate = dataUpdate,
            starsMaxLocal = 0,
            starsMaxRemote = starsMaxRemote,
            picture = picture,
            ratingRemote = ratingRemote,
            ratingLocal = 0,
            isShowArchive = false,
            isShowDownload = false
        )
    }
}

data class SubCategoryData(
    val id: Int = 0,
    val subSubcategory: List<SubsubCategoryData> = emptyList(),
    val nameQuiz: String = "",
    val dataUpdate: String = "",
    val userName: String = "",
    val starsMaxRemote: Int = 0,
    val picture: String? = null,
    val ratingRemote: Int = 0
) {
    // Конструктор по умолчанию для Firestore
    constructor() : this(0, emptyList(), "", "", "", 0, null, 0)

    fun toSubCategoryDataLocal(): com.tpov.common.data.model.local.SubCategoryData {
        return com.tpov.common.data.model.local.SubCategoryData(
            id = id,
            subSubcategory = subSubcategory.map { it.toSubsubCategoryDataLocal() },
            nameQuiz = nameQuiz,
            dataUpdate = dataUpdate,
            userName = userName,
            starsMaxLocal = 0,
            starsMaxRemote = starsMaxRemote,
            picture = picture,
            ratingRemote = ratingRemote,
            ratingLocal = 0,
            isShowArchive = false,
            isShowDownload = false
        )
    }
}

data class SubsubCategoryData(
    val id: Int = 0,
    val quizData: List<QuizData> = emptyList(),
    val nameQuiz: String = "",
    val dataUpdate: String = "",
    val userName: String = "",
    val starsMaxRemote: Int = 0,
    val picture: String? = null,
    val ratingRemote: Int = 0
) {
    // Конструктор по умолчанию для Firestore
    constructor() : this(0, emptyList(), "", "", "", 0, null, 0)

    fun toSubsubCategoryDataLocal(): com.tpov.common.data.model.local.SubsubCategoryData {
        return com.tpov.common.data.model.local.SubsubCategoryData(
            id = id,
            quizData = quizData.map { it.toQuizDataLocal() },
            nameQuiz = nameQuiz,
            dataUpdate = dataUpdate,
            userName = userName,
            starsMaxLocal = 0,
            starsMaxRemote = starsMaxRemote,
            picture = picture,
            ratingRemote = ratingRemote,
            ratingLocal = 0,
            isShowArchive = false,
            isShowDownload = false
        )
    }
}

data class QuizData(
    val idQuiz: Int = 0,
    val nameQuiz: String = "",
    val ratingRemote: Int = 0,
    val dataUpdate: String = "",
    val userName: String = "",
    val picture: String = "",
    val starsMaxRemote: Int = 0
) {
    // Конструктор по умолчанию для Firestore
    constructor() : this(0, "", 0, "", "", "", 0)

    fun toQuizDataLocal(): com.tpov.common.data.model.local.QuizData {
        return com.tpov.common.data.model.local.QuizData(
            idQuiz = idQuiz,
            nameQuiz = nameQuiz,
            ratingRemote = ratingRemote,
            ratingLocal = 0,
            isShowArchive = false,
            isShowDownload = false,
            dataUpdate = dataUpdate,
            userName = userName,
            starsMaxLocal = 0,
            picture = picture,
            starsMaxRemote = starsMaxRemote
        )
    }
}
