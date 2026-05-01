package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

@JvmInline
value class DraftSectionId(val value: String) {
    init {
        require(value.isNotBlank()) { "DraftSectionId.value must not be blank" }
    }
}
