package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider

fun interface QuestAuthoringTimestampProvider {
    fun nowMs(): Long
}
