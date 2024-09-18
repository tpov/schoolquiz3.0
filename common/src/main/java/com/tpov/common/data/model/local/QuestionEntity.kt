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
    val id: Int? = null,

    @ColumnInfo(name = "numQuestion")
    var numQuestion: Int = 0,

    @ColumnInfo(name = "nameQuestion")
    var nameQuestion: String = "",

    @ColumnInfo(name = "pictureQuestion")
    var pathPictureQuestion: String? = "",

    @ColumnInfo(name = "answer")
    val answer: Int = 0,

    @ColumnInfo(name = "nameAnswers")
    val nameAnswers: String = "",

    @ColumnInfo(name = "hardQuestion")
    val hardQuestion: Boolean = false,

    @ColumnInfo(name = "idQuiz")
    val idQuiz: Int = 0,

    @ColumnInfo(name = "language")
    var language: String = "",

    @ColumnInfo(name = "lvlTranslate")
    var lvlTranslate: Int = 0
) : Parcelable {
    fun toQuestionRemote() = QuestionRemote(
        answer = this.answer,
        nameAnswers = this.nameAnswers,
        pathPictureQuestion = this.pathPictureQuestion,
        nameQuestion = this.nameAnswers,
        lvlTranslate = this.lvlTranslate,
        numQuestion = this.numQuestion,
        hardQuestion = this.hardQuestion,
        language = this.language
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
