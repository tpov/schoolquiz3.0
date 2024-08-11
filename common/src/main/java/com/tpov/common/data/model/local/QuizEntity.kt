package com.tpov.common.data.model.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.model.remote.QuizRemote

@Entity(tableName = "quiz_entity")
data class QuizEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null,

    @ColumnInfo(name = "idCategory")
    val idCategory: Int = 0,

    @ColumnInfo(name = "idSubcategory")
    val idSubcategory: Int = 0,

    @ColumnInfo(name = "idSubsubcategory")
    val idSubsubcategory: Int = 0,

    @ColumnInfo(name = "nameQuiz")
    val nameQuiz: String = "",

    @ColumnInfo(name = "userName")
    val userName: String = "",

    @ColumnInfo(name = "dataUpdate")
    val dataUpdate: String = "",

    @ColumnInfo(name = "starsMaxLocal")
    var starsMaxLocal: Int = 0,

    @ColumnInfo(name = "starsRemote")
    val starsMaxRemote: Int = 0,

    @ColumnInfo(name = "numQ")
    val numQ: Int = 0,

    @ColumnInfo(name = "numHQ")
    val numHQ: Int = 0,

    @ColumnInfo(name = "starsAverageLocal")
    var starsAverageLocal: Int = 0,

    @ColumnInfo(name = "starsAverageRemote")
    val starsAverageRemote: Int = 0,

    @ColumnInfo(name = "versionQuiz")
    val versionQuiz: Int = 0,

    @ColumnInfo(name = "picture")
    val picture: String? = "",

    @ColumnInfo(name = "event")
    var event: Int = 0,

    @ColumnInfo(name = "ratingLocal")
    val ratingLocal: Int = 0,

    @ColumnInfo(name = "ratingRemote")
    val ratingRemote: Int = 0,

    @ColumnInfo(name = "tpovId")
    var tpovId: Int = 0,

    @ColumnInfo(name = "languages")
    var languages: String = ""
) {
    fun toQuizRemote(): QuizRemote {
        return QuizRemote(
            nameQuiz = this.nameQuiz,
            tpovId = this.tpovId,
            dataUpdate = this.dataUpdate,
            versionQuiz = this.versionQuiz,
            picture = this.picture ?: "",
            event = this.event,
            numQ = this.numQ,
            numHQ = this.numHQ,
            starsAverageRemote = this.starsAverageRemote,
            starsMaxRemote = this.starsMaxRemote,
            ratingRemote = this.ratingRemote,
            userName = this.userName,
            languages = this.languages
        )
    }

    constructor() : this(
        id = null,
        idCategory = 0,
        idSubcategory = 0,
        idSubsubcategory = 0,
        nameQuiz = "",
        userName = "",
        dataUpdate = "",
        starsMaxLocal = 0,
        starsMaxRemote = 0,
        numQ = 0,
        numHQ = 0,
        starsAverageLocal = 0,
        starsAverageRemote = 0,
        versionQuiz = 0,
        picture = null,
        event = 0,
        ratingLocal = 0,
        ratingRemote = 0,
        tpovId = 0,
        languages = ""
    )
}