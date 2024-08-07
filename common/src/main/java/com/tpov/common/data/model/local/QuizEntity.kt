package com.tpov.common.data.model.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_entity")
data class QuizEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int?,

    @ColumnInfo(name = "idCategory")
    val idCategory: Int,

    @ColumnInfo(name = "idSubcategory")
    val idSubcategory: Int,

    @ColumnInfo(name = "idSubsubcategory")
    val idSubsubcategory: Int,

    @ColumnInfo(name = "nameQuiz")
    val nameQuiz: String,

    @ColumnInfo(name = "userName")
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
    var tpovId: Int,

    @ColumnInfo(name = "languages")
    var languages: String
) {
    constructor() : this(
        0, 0,0,0, "", "", "", 0, 0, 0, 0, 0, 0, 0, "", 0, 0, 0, false, 0, ""
    )
}
