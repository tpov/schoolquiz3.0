package com.tpov.common.data.model.remote

import com.google.firebase.firestore.IgnoreExtraProperties
import com.tpov.common.data.model.local.QuizEntity

@IgnoreExtraProperties
data class Quiz (
    var nameQuiz: String = "",
    var tpovId: Int = 0,
    var dataUpdate: String = "",
    var versionQuiz: Int = -1,
    var picture: String = "123",
    var event: Int = -1,
    var numQ: Int,
    var numHQ: Int,
    var starsAverageRemote: Int,
    var starsMaxRemote: Int,
    var ratingRemote: Int,
    var userName: String,
    var languages: String
) {
    fun toQuizEntity(
        id: Int,
        idCategory: Int,
        idSubcategory: Int,
        idSubsubcategory: Int,
        starsMaxLocal: Int,
        starsAverageLocal: Int,
        ratingLocal: Int
    ): QuizEntity {
        return QuizEntity(
            id = id,
            idCategory = idCategory,
            idSubcategory = idSubcategory,
            idSubsubcategory = idSubsubcategory,
            nameQuiz = nameQuiz,
            userName = userName,
            dataUpdate = dataUpdate,
            starsMaxLocal = starsMaxLocal,
            starsMaxRemote = starsMaxRemote,
            numQ = numQ,
            numHQ = numHQ,
            starsAverageLocal = starsAverageLocal,
            starsAverageRemote = starsAverageRemote,
            versionQuiz = versionQuiz,
            picture = picture,
            event = event,
            ratingLocal = ratingLocal,
            ratingRemote = ratingRemote,
            tpovId = tpovId,
            languages = languages
        )
    }

    constructor() : this(
        "", 0, "", -1, "", -1, 0, 0, 0,"", ""
    )
}

