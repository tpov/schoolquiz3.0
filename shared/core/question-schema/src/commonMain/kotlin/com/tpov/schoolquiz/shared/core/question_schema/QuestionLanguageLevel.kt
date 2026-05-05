package com.tpov.schoolquiz.shared.core.question_schema

object QuestionLanguageLevel {
    const val MIN: Int = 0
    const val DEFAULT: Int = 1

    fun requireValid(
        value: Int,
        fieldName: String,
    ) {
        require(value >= MIN) {
            "$fieldName must be >= $MIN, got $value"
        }
    }
}
