package com.tpov.common.data.model.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.model.remote.QuestionDetailRemote

@Entity(tableName = "question_detail_entity")

data class QuestionDetailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int?,

    @ColumnInfo(name = "event")
    val event: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "subCategory")
    val subCategory: String,

    @ColumnInfo(name = "subsubCategory")
    val subsubCategory: String,

    @ColumnInfo(name = "quiz")
    val quiz: String,

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
        quiz = "",
        event = "",
        category = "",
        subCategory = "",
        subsubCategory = "",
        data = "",
        codeAnswer = null,
        hardQuiz = false,
        synth = false
    )
}
