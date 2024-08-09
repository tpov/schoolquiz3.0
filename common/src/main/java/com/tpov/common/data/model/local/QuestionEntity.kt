package com.tpov.common.data.model.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.model.remote.QuestionRemote


@Entity(tableName = "question_entity")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int?,

    @ColumnInfo(name = "numQuestion")
    val numQuestion: Int,

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
) {
    fun toQuestionRemote(): QuestionRemote {
        return QuestionRemote(
            nameQuestion = this.nameQuestion,
            answer = this.answer,
            nameAnswers = this.nameAnswers,
            pathPictureQuestion = this.pathPictureQuestion,
            hardQuestion = this.hardQuestion
        )
    }
}
