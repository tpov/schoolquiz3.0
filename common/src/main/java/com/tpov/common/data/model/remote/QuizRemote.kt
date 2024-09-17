package com.tpov.common.data.model.remote

import com.google.firebase.firestore.IgnoreExtraProperties
import com.tpov.common.data.model.local.QuizEntity

@IgnoreExtraProperties
data class QuizRemote(
    var nameQuiz: String = "",
    var tpovId: Int = 0,
    var dataUpdate: String = "",
    var versionQuiz: Int = -1,
    var picture: String = "",
    var event: Int = 1,
    var numQ: Int = 0,
    var numHQ: Int = 0,
    var starsAverageRemote: Int = 0,
    var starsMaxRemote: Int = 0,
    var ratingRemote: Int = 0,
    var userName: String = "",
    var languages: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "nameQuiz" to nameQuiz,
            "tpovId" to tpovId,
            "dataUpdate" to dataUpdate,
            "versionQuiz" to versionQuiz,
            "picture" to picture,
            "event" to event,
            "numQ" to numQ,
            "numHQ" to numHQ,
            "starsAverageRemote" to starsAverageRemote,
            "starsMaxRemote" to starsMaxRemote,
            "ratingRemote" to ratingRemote,
            "userName" to userName,
            "languages" to languages
        )
    }

    fun toQuizEntity(
        id: Int = 0,
        idCategory: Int,
        idSubcategory: Int,
        idSubsubcategory: Int,
        starsMaxLocal: Int = 0,
        starsAverageLocal: Int = 0,
        ratingLocal: Int = 0
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
        nameQuiz = "",
        tpovId = 0,
        dataUpdate = "",
        versionQuiz = 0,
        picture = "",
        event = 0,
        numQ = 0,
        numHQ = 0,
        starsAverageRemote = 0,
        starsMaxRemote = 0,
        ratingRemote = 0,
        userName = "",
        languages = ""
    )
}