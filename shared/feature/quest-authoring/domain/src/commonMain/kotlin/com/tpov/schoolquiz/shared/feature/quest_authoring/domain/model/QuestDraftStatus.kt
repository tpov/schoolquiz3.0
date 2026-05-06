package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

enum class QuestDraftStatus {
    DRAFT,
    SAVED,
    SYNC_PENDING,
    SYNCED,
    CONFLICT,
    REVIEW_QUEUED,
    REVIEW_SENT,
    PUBLISHED,
}
