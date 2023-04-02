package com.tpov.schoolquiz.data.fierbase

import android.graphics.Bitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties
import com.tpov.schoolquiz.data.database.entities.QuizEntity

@IgnoreExtraProperties
data class Quiz  constructor(
    var nameQuiz: String = "",
    var idQuiz: Int = -1,
    var tpovId: Int = 0,
    var data: String = "",
    var versionQuiz: Int = -1,
    var picture: String = "123",
    var event: Int = -1
)


fun quizToQuizEntity(
    quiz: Quiz,
    userName: String,
    stars: Int,
    numQ: Int,
    numHQ: Int,
    starsAll: Int,
    rating: Int,
    picture: String?
): QuizEntity {
    return QuizEntity(
        id = null,
        nameQuiz = quiz.nameQuiz,
        userName = userName,
        data = quiz.data,
        stars = stars,
        numQ = numQ,
        numHQ = numHQ,
        starsAll = starsAll,
        versionQuiz = quiz.versionQuiz,
        picture = picture,
        event = quiz.event,
        rating = rating,
        tpovId = quiz.tpovId
    )
}