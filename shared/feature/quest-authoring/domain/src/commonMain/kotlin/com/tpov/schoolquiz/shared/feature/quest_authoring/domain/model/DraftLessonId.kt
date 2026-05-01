package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

@JvmInline
value class DraftLessonId(val value: String) {
    init {
        require(value.isNotBlank()) { "DraftLessonId.value must not be blank" }
    }
}
