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

/**
 * The statuses that mean the draft exists on the server as well as on this device.
 *
 * Declared once, here, because the answer decides what a database rebuild costs: a draft in one of
 * these states can be fetched again afterwards, and a draft in any other state exists nowhere else
 * and is simply gone. Spelling the set out at each call site would let the two readings drift, and
 * the one that drifts quietly is the one that loses somebody's work.
 *
 * `REVIEW_QUEUED` is deliberately outside the set: at that moment the submission is still sitting in
 * a local queue, so nothing has reached the server yet.
 */
val QUEST_DRAFT_STATUSES_REACHED_SERVER: Set<QuestDraftStatus> = setOf(
    QuestDraftStatus.SYNCED,
    QuestDraftStatus.REVIEW_SENT,
    QuestDraftStatus.PUBLISHED,
)

/** Whether this draft has a server copy, and so survives the local database being rebuilt. */
val QuestDraftStatus.hasReachedServer: Boolean
    get() = this in QUEST_DRAFT_STATUSES_REACHED_SERVER
