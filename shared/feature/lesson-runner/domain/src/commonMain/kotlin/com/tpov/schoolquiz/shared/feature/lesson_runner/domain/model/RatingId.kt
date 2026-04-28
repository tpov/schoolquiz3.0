package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

@JvmInline
value class RatingId(val value: String) {
    init {
        require(value.isNotBlank()) { "RatingId must not be blank" }
    }
}
