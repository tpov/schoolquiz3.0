package com.tpov.schoolquiz.shared.core.question_schema

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class CandidateId(val raw: String) {
    init {
        require(raw.isNotBlank()) { "CandidateId must not be blank" }
    }
}
