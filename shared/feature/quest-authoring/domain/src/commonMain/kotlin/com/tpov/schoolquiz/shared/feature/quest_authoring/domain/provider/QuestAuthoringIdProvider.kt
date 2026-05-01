package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider

fun interface QuestAuthoringIdProvider {
    fun nextId(prefix: String): String
}
