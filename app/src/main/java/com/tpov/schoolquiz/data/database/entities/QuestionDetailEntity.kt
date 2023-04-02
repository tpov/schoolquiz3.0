package com.tpov.schoolquiz.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "table_data")

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
    val hardQuiz: Boolean
)