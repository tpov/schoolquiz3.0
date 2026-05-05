package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

interface QuestArenaSubmissionRemoteDataSource {
    suspend fun submit(request: QuestArenaSubmissionRequest)
}
