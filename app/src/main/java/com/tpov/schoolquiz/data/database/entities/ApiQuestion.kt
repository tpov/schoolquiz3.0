package com.tpov.schoolquiz.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "table_generate_question")

data class ApiQuestion(
@PrimaryKey(autoGenerate = true)
val id: Int?,

@ColumnInfo(name = "date")
val date: String,

@ColumnInfo(name = "question")
val question: String,

@ColumnInfo(name = "answer")
val answer: String,

@ColumnInfo(name = "questionTranslate")
val questionTranslate: String,

@ColumnInfo(name = "answerTranslate")
val answerTranslate: String
)
