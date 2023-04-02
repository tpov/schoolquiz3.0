package com.tpov.schoolquiz.data.fierbase

data class QuestionDetail(
    val idQuiz: String,
    val data: String,
    val codeAnswer: String?,
    val hardQuiz: Boolean,
    val nameUser: String,
    val rating: Int
)