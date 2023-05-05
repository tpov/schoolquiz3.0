package com.tpov.schoolquiz.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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

    @ColumnInfo(name = "starsPlayer")
    val starsPlayer: Int,

    @ColumnInfo(name = "numQ")
    val numQ: Int,

    @ColumnInfo(name = "numHQ")
    val numHQ: Int,

    @ColumnInfo(name = "starsAll")
    val starsAll: Int,

    @ColumnInfo(name = "starsAllPlayer")
    val starsAllPlayer: Int,

    @ColumnInfo(name = "versionQuiz")
    val versionQuiz: Int,

    @ColumnInfo(name = "picture")
    val picture: String?,

    @ColumnInfo(name = "event")
    var event: Int,

    @ColumnInfo(name = "rating")
    val rating: Int,

    @ColumnInfo(name = "ratingPlayer")
    val ratingPlayer: Int,

    @ColumnInfo(name = "showDeleteButton")
    var showItemMenu: Boolean = false,

    @ColumnInfo(name = "tpovId")
    var tpovId: Int
) {
    constructor() : this(
        0, "", "", "", 0, 0, 0, 0, 0, 0, 0, "", 0, 0, 0, false, 0
    )
}
