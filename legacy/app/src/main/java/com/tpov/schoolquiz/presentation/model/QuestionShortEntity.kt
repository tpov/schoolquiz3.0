package com.tpov.schoolquiz.presentation.model

//Это тот же QuestionEntity, но оставлены ключевые параметры и вопросы на одном языке, используется для отображение вопросов для спинера
data class QuestionShortEntity(
    var id: Int?,
    val numQuestion: Int,
    val nameQuestion: String,
    val hardQuestion: Boolean
)