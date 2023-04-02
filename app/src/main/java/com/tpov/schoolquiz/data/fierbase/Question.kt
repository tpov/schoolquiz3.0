package com.tpov.schoolquiz.data.fierbase

import com.google.firebase.database.IgnoreExtraProperties
import com.tpov.schoolquiz.data.database.entities.QuestionEntity

@IgnoreExtraProperties
data class Question (
    val nameQuestion: String,
    val answerQuestion: Boolean,
    val typeQuestion: Boolean,
    val lvlTranslate: Int
) {
    constructor() : this(
        "", false, false, 0
    )
}

fun QuestionEntity.toQuestion(): Question {
    return Question(
        nameQuestion = this.nameQuestion,
        answerQuestion = this.answerQuestion,
        typeQuestion = this.hardQuestion,
        lvlTranslate = this.lvlTranslate
    )
}
