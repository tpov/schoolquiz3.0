package com.tpov.common.data.model.remote

import com.google.firebase.firestore.IgnoreExtraProperties
import com.tpov.common.data.model.local.QuestionEntity

@IgnoreExtraProperties
data class Question (
    val nameQuestion: String,
    val answer: Int,
    val nameAnswers: String,
    val pathPictureQuestion: String?,
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
