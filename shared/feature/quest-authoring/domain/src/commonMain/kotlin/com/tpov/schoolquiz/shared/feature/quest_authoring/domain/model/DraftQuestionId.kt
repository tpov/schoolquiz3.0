package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

@JvmInline
value class DraftQuestionId(val value: String) {
    init {
        require(value.isNotBlank()) { "DraftQuestionId.value must not be blank" }
    }
}
