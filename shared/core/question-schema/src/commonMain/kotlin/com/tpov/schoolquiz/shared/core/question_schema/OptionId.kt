package com.tpov.schoolquiz.shared.core.question_schema

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class OptionId(val raw: String) {
    init {
        require(raw.isNotBlank()) { "OptionId must not be blank" }
    }
}
