package com.tpov.schoolquiz.shared.feature.quest_authoring.data.provider

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import kotlinx.datetime.Clock

class DefaultQuestAuthoringTimestampProvider : QuestAuthoringTimestampProvider {
    override fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}
