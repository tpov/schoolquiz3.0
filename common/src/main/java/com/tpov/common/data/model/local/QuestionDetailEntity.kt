package com.tpov.common.data.model.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.model.remote.QuestionDetailRemote

@Entity(tableName = "question_detail_entity")

data class QuestionDetailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int?,

    @ColumnInfo(name = "idEvent")
    val idEvent: Int,

    @ColumnInfo(name = "idCategory")
    val idCategory: Int,

    @ColumnInfo(name = "idSubCategory")
    val idSubCategory: Int,

    @ColumnInfo(name = "idSubsubCategory")
    val idSubsubCategory: Int,

    @ColumnInfo(name = "idQuiz")
    val idQuiz: Int,

    @ColumnInfo(name = "data")
    val data: String,

    @ColumnInfo(name = "codeAnswer")
    val codeAnswer: String?,

    @ColumnInfo(name = "hardQuiz")
    val hardQuiz: Boolean,

    @ColumnInfo(name = "synth")
    val synth: Boolean
) {
    fun toQuestionDetailRemote() = QuestionDetailRemote(
        data, codeAnswer, hardQuiz
    )

    constructor() : this(
        id = null,
        idQuiz = -1,
        idEvent = -1,
        idCategory = -1,
        idSubCategory = -1,
        idSubsubCategory = -1,
        data = "",
        codeAnswer = null,
        hardQuiz = false,
        synth = false
    )
}