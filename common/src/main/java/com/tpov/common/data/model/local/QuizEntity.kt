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

    @ColumnInfo(name = "dataUpdate")
    val dataUpdate: String,

    @ColumnInfo(name = "starsMaxLocal")
    val starsMaxLocal: Int,

    @ColumnInfo(name = "starsRemote")
    val starsMaxRemote: Int,

    @ColumnInfo(name = "numQ")
    val numQ: Int,

    @ColumnInfo(name = "numHQ")
    val numHQ: Int,

    @ColumnInfo(name = "starsAverageLocal")
    val starsAverageLocal: Int,

    @ColumnInfo(name = "starsAverageRemote")
    val starsAverageRemote: Int,

    @ColumnInfo(name = "versionQuiz")
    val versionQuiz: Int,

    @ColumnInfo(name = "picture")
    val picture: String?,

    @ColumnInfo(name = "event")
    var event: Int,

    @ColumnInfo(name = "ratingLocal")
    val ratingLocal: Int,

    @ColumnInfo(name = "ratingRemote")
    val ratingRemote: Int,

    @ColumnInfo(name = "tpovId")
    var tpovId: Int,

    @ColumnInfo(name = "languages")
    var languages: String
) {
    constructor() : this(
        0, 0,0,0, "", "", "", 0, 0, 0, 0, 0, 0, 0, "", 0, 0, 0, false, 0, ""
    )
}
