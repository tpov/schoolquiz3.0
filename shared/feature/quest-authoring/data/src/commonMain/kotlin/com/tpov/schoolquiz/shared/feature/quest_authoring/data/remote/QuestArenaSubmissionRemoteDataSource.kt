package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

/** One bounded, authenticated request for review outcomes the device is still waiting for. */
data class QuestSubmissionOutcomeRequest(
    val submissionIds: Set<String>,
    val legacyDraftIds: Set<String>,
) {
    init {
        require(submissionIds.intersect(legacyDraftIds).isEmpty()) {
            "A review submission id and a legacy draft id must not share an identifier"
        }
        require(submissionIds.size + legacyDraftIds.size <= MAX_QUEST_REVIEW_OUTCOME_TARGETS) {
            "At most $MAX_QUEST_REVIEW_OUTCOME_TARGETS review outcomes may be read at once"
        }
        require((submissionIds + legacyDraftIds).all { it.isNotBlank() }) {
            "Review outcome identifiers must not be blank"
        }
    }
}

/** What the server decided about a submission the author sent. */
data class QuestSubmissionOutcome(
    val submissionId: String,
    val draftId: String,
    val status: String,
    val rejectionReason: String?,
) {
    val isRejected: Boolean get() = status == STATUS_REJECTED

    /** Опубликовано. Такой же терминальный исход, как отказ, и автору о нём тоже надо сказать. */
    val isPublished: Boolean get() = status == STATUS_PUBLISHED

    companion object {
        const val STATUS_REJECTED = "REJECTED"
        const val STATUS_PUBLISHED = "PUBLISHED"
    }
}

const val MAX_QUEST_REVIEW_OUTCOME_TARGETS: Int = 20

/**
 * Чтение вердиктов. Отправки здесь больше нет.
 *
 * Заявка уходит единственным приёмником (AD-6): прямая запись в `quest_review_requests` обходила
 * проверку ключа идемпотентности в принципе — проверить ключ можно только там, где выполняется
 * код, а правило Firestore кода не выполняет.
 */
interface QuestArenaSubmissionRemoteDataSource {
    /**
     * Reads back only a named, bounded set of still-pending outcomes.
     *
     * The server derives the owner exclusively from Firebase Auth. Current clients name their
     * submission IDs exactly. [QuestSubmissionOutcomeRequest.legacyDraftIds] exists solely for
     * upgrading rows created before the local active-submission marker was introduced; it is
     * intentionally bounded and never becomes a history scan.
     */
    suspend fun fetchOutcomes(request: QuestSubmissionOutcomeRequest): List<QuestSubmissionOutcome> = emptyList()
}
