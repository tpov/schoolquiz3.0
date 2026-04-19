package com.tpov.common.data.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.model.local.QuestionDetailLocal
import com.tpov.common.data.model.remote.QuestionDetailRemote
import com.tpov.common.presentation.model.PathStructure

@Entity(tableName = "question_detail_entity")

data class QuestionDetailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int?,

    @ColumnInfo(name = "eventName")
    val eventName: String,

    @ColumnInfo(name = "categoryName")
    val categoryName: String,

    @ColumnInfo(name = "subCategoryName")
    val subCategoryName: String,

    @ColumnInfo(name = "subsubCategoryName")
    val subsubCategoryName: String,

    @ColumnInfo(name = "quizName")
    val quizName: String,

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
        data, codeAnswer, hardQuiz, categoryName, subCategoryName, subsubCategoryName, quizName
    )

    fun toQuestionDetailLocal() = QuestionDetailLocal(
        id = id,
        pathStructure = PathStructure(eventName, categoryName, subCategoryName,subsubCategoryName, quizName),
        data, codeAnswer, hardQuiz
    )

    constructor() : this(
        id = null,
        quizName = "",
        eventName = "",
        categoryName = "",
        subCategoryName = "",
        subsubCategoryName = "",
        data = "",
        codeAnswer = null,
        hardQuiz = false,
        synth = false
    )

}
