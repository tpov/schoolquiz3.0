package com.tpov.common.data.model.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.model.remote.QuestionDetailRemote

@Entity(tableName = "question_detail_entity")

data class QuestionDetailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int?,

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
    fun toQuestionDetail() = QuestionDetailRemote(
        data, codeAnswer, hardQuiz
    )

    constructor() : this(
        id = null,
        idQuiz = -1,
        data = "",
        codeAnswer = null,
        hardQuiz = false,
        synth = false
    )
}