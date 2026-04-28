package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

@JvmInline
value class AttemptId(val value: String) {
    init {
        require(value.isNotBlank()) { "AttemptId must not be blank" }
    }
}
