package com.tpov.schoolquiz.data.fierbase

import android.graphics.Bitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
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
    var starsAll: Int,
    var rating: Int,
    var userName: String,
) {
    constructor() : this(
        "", 0, "", -1, "", -1, 0, 0, 0, 0, ""
    )
}


fun quizToQuizEntity(
    quiz: Quiz,
    stars: Int,
    picture: String?
): QuizEntity {
    return QuizEntity(
        id = null,
        nameQuiz = quiz.nameQuiz,
        userName = quiz.userName,
        data = quiz.data,
        stars = stars,
        numQ = quiz.numQ,
        numHQ = quiz.numHQ,
        starsAll = quiz.starsAll,
        versionQuiz = quiz.versionQuiz,
        picture = picture,
        event = quiz.event,
        rating = quiz.rating,
        tpovId = quiz.tpovId
    )
}