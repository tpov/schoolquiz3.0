package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

enum class QuestDraftStatus {
    DRAFT,
    SYNC_PENDING,
    SYNCED_PRIVATE,
    CONFLICT,
    REVIEW_QUEUED,
    REVIEW_SENT,
    PUBLISHED,
}
