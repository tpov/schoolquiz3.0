package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

@JvmInline
value class QuestDraftId(val value: String) {
    init {
        require(value.isNotBlank()) { "QuestDraftId.value must not be blank" }
    }
}
