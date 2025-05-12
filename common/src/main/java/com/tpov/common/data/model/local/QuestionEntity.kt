package com.tpov.common.data.model.local

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.model.remote.QuestionRemote
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "question_entity")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null,

    @ColumnInfo(name = "numQuestion")
    var numQuestion: Int = 0,

    @ColumnInfo(name = "nameQuestion")
    var nameQuestion: String = "",

    @ColumnInfo(name = "pictureQuestion")
    var pathPictureQuestion: String? = "",

    @ColumnInfo(name = "nameAnswers")
    var nameAnswers: String = "",

    @ColumnInfo(name = "hardQuestion")
    var hardQuestion: Boolean = false,

    @ColumnInfo(name = "event")
    var event: String,

    @ColumnInfo(name = "category")
    var category: String,

    @ColumnInfo(name = "subCategory")
    var subCategory: String,

    @ColumnInfo(name = "subsubCategory")
    var subsubCategory: String,

    @ColumnInfo(name = "quiz")
    var quiz: String,

    @ColumnInfo(name = "language")
    var language: String = "",

    @ColumnInfo(name = "lvlTranslate")
    var lvlTranslate: Int = 0

) : Parcelable {
    fun toQuestionRemote() = QuestionRemote(
        nameAnswers = this.nameAnswers,
        pathPictureQuestion = this.pathPictureQuestion,
        nameQuestion = this.nameQuestion,
        lvlTranslate = this.lvlTranslate,
        numQuestion = this.numQuestion,
        hardQuestion = this.hardQuestion,
        language = this.language
    )

    constructor() : this(
        id = null,
        numQuestion = 0,
        nameQuestion = "",
        nameAnswers = "",
        hardQuestion = false,
        event = "", category = "", subCategory = "", subsubCategory = "", quiz = "",
        language = "",
        lvlTranslate = 0,
        pathPictureQuestion = ""
    )

}
