package com.tpov.schoolquiz.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "new_user_table")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int?,

    @ColumnInfo(name = "numQuestion")
    val numQuestion: Int,

    @ColumnInfo(name = "nameQuestion")
    val nameQuestion: String,

    @ColumnInfo(name = "answerQuestion")
    val answerQuestion: Boolean,

    @ColumnInfo(name = "hardQuestion")
    val hardQuestion: Boolean,

    @ColumnInfo(name = "idQuiz")
    val idQuiz: Int,

    @ColumnInfo(name = "language")
    val language: String,

    @ColumnInfo(name = "lvlTranslate")
    val lvlTranslate: Int
)
