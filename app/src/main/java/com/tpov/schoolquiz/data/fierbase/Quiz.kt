package com.tpov.schoolquiz.data.fierbase

import com.google.firebase.database.IgnoreExtraProperties
import com.tpov.schoolquiz.data.database.entities.QuizEntity

@IgnoreExtraProperties
data class Quiz (
    var nameQuiz: String = "",
    var tpovId: Int = 0,
    var data: String = "",
    var versionQuiz: Int = -1,
    var picture: String = "123",
    var event: Int = -1,
    var numQ: Int,
    var numHQ: Int,
    var starsAllPlayer: Int,
    var starsPlayer: Int,
    var ratingPlayer: Int,
    var userName: String,
    var languages: String
) {
    constructor() : this(
        "", 0, "", -1, "", -1, 0, 0, 0, 0, 0,"", ""
    )
}

fun Quiz.toQuizEntity(
    id: Int,
    stars: Int,
    starsAll: Int,
    rating: Int,
    picture: String?
): QuizEntity {
    return QuizEntity(
        id = id,
        nameQuiz = nameQuiz,
        userName = userName,
        data = data,
        stars = stars,
        numQ = numQ,
        numHQ = numHQ,
        starsAllPlayer = starsAllPlayer,
        versionQuiz = versionQuiz,
        picture = picture,
        event = event,
        rating = rating,
        tpovId = tpovId,
        starsAll = starsAll,
        starsPlayer = starsPlayer,
        ratingPlayer = ratingPlayer,
        languages = languages
    )
}
