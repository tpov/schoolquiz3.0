package com.tpov.schoolquiz.presentation.model

data class QuestionShortEntity(
    var id: Int?,
    val numQuestion: Int,
    val nameQuestion: String,
    val hardQuestion: Boolean
)