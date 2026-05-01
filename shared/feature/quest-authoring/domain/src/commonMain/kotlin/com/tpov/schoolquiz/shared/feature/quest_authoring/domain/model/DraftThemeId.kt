package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

@JvmInline
value class DraftThemeId(val value: String) {
    init {
        require(value.isNotBlank()) { "DraftThemeId.value must not be blank" }
    }
}
