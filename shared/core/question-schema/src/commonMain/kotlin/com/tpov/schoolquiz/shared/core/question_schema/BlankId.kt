package com.tpov.schoolquiz.shared.core.question_schema

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class BlankId(val raw: String) {
    init {
        require(raw.isNotBlank()) { "BlankId must not be blank" }
    }
}
