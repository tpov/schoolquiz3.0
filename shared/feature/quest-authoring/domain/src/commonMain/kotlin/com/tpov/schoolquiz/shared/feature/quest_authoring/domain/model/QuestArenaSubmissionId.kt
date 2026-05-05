package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

@JvmInline
value class QuestArenaSubmissionId(val value: String) {
    init {
        require(value.isNotBlank()) { "QuestArenaSubmissionId.value must not be blank" }
    }
}
