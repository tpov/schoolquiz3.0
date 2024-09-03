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
    val id: Int?,

    @ColumnInfo(name = "numQuestion")
    var numQuestion: Int,

    @ColumnInfo(name = "nameQuestion")
    var nameQuestion: String,

    @ColumnInfo(name = "pictureQuestion")
    var pathPictureQuestion: String?,

    @ColumnInfo(name = "answer")
    val answer: Int,

    @ColumnInfo(name = "nameAnswers")
    val nameAnswers: String,

    @ColumnInfo(name = "hardQuestion")
    val hardQuestion: Boolean,

    @ColumnInfo(name = "idQuiz")
    val idQuiz: Int,

    @ColumnInfo(name = "language")
    var language: String,

    @ColumnInfo(name = "lvlTranslate")
    var lvlTranslate: Int
) : Parcelable {
    fun toQuestionRemote() = QuestionRemote(
        answer = this.answer,
        nameAnswers = this.nameAnswers,
        pathPictureQuestion = this.pathPictureQuestion,
        nameQuestion = this.nameAnswers,
        lvlTranslate = this.lvlTranslate
    )


    constructor() : this(
        id = null,
        numQuestion = 0,
        nameQuestion = "",
        answer = 0,
        nameAnswers = "",
        hardQuestion = false,
        idQuiz = 0,
        language = "",
        lvlTranslate = 0,
        pathPictureQuestion = ""
    )
}
