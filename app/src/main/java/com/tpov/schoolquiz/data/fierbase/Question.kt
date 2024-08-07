package com.tpov.schoolquiz.data.fierbase

import com.google.firebase.database.IgnoreExtraProperties
import com.tpov.schoolquiz.data.database.entities.QuestionEntity

@IgnoreExtraProperties
data class Question (
    val nameQuestion: String,
    val answer: Int,
    val nameAnswers: String,
    val pathPictureQuestion: String,
    val hardQuestion: Boolean,
) {
    constructor() : this(
        "", 0, "", "",  false
    )
}

fun QuestionEntity.toQuestion(): Question {
    return Question(
        nameQuestion = this.nameQuestion,
        answer = this.answer,
        nameAnswers = this.nameAnswers,
        pathPictureQuestion = this.pathPictureQuestion,
        hardQuestion = this.hardQuestion
    )
}
