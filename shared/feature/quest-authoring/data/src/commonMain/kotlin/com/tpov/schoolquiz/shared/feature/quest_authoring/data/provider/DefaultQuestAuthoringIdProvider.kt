package com.tpov.schoolquiz.shared.feature.quest_authoring.data.provider

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import kotlin.random.Random

class DefaultQuestAuthoringIdProvider : QuestAuthoringIdProvider {
    override fun nextId(prefix: String): String {
        require(prefix.isNotBlank()) { "prefix must not be blank" }
        return "$prefix-${Random.nextLong(1, Long.MAX_VALUE).toString(36)}"
    }
}
