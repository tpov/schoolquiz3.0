package com.tpov.schoolquiz.data.database.entities

import android.graphics.Bitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.schoolquiz.data.fierbase.Quiz

@Entity(tableName = "front_list")
data class QuizEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int?,

    @ColumnInfo(name = "nameQuiz")
    val nameQuiz: String,

    @ColumnInfo(name = "user_name")
    val userName: String,

    @ColumnInfo(name = "data")
    val data: String,

    @ColumnInfo(name = "stars")
    val stars: Int,

    @ColumnInfo(name = "numQ")
    val numQ: Int,

    @ColumnInfo(name = "numHQ")
    val numHQ: Int,

    @ColumnInfo(name = "starsAll")
    val starsAll: Int,

    @ColumnInfo(name = "versionQuiz")
    val versionQuiz: Int,

    @ColumnInfo(name = "picture")
    val picture: String?,

    @ColumnInfo(name = "event")
    var event: Int,

    @ColumnInfo(name = "rating")
    val rating: Int,

    @ColumnInfo(name = "showDeleteButton")
    var showDeleteButton: Boolean = false,

    @ColumnInfo(name = "tpovId")
    var tpovId: Int
) {
    constructor() : this(
        0, "", "", "", 0, 0, 0, 0, 0, "", 0, 0, false, 0
    )
}

fun Quiz.toQuizEntity(stars: Int, picture: String?, idQuiz: Int): QuizEntity {
    return QuizEntity(
        id = idQuiz,
        nameQuiz = nameQuiz,
        userName = userName,
        data = data,
        stars = stars,
        numQ = numQ,
        numHQ = numHQ,
        starsAll = starsAll,
        versionQuiz = versionQuiz,
        picture = picture,
        tpovId = tpovId,
        event = event,
        rating = rating,
        showDeleteButton = true
    )
}
