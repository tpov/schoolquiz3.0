package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

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

/**
 * Чтение вердиктов. Отправки здесь больше нет.
 *
 * Заявка уходит единственным приёмником (AD-6): прямая запись в `quest_review_requests` обходила
 * проверку ключа идемпотентности в принципе — проверить ключ можно только там, где выполняется
 * код, а правило Firestore кода не выполняет.
 */
interface QuestArenaSubmissionRemoteDataSource {
    /**
     * Reads back what the server decided about this author's submissions.
     *
     * Queried by owner rather than tracked locally on purpose: the outbox row is deleted the
     * moment the submission is sent, so the device keeps no handle on it. The author's own
     * requests are readable by the security rules, and each document names its draft — which is
     * all that is needed to put the verdict back on the right draft.
     *
     * Without this the author learns nothing at all: not a rejection, and not a publication.
     */
    suspend fun fetchOutcomes(ownerUid: String): List<QuestSubmissionOutcome> = emptyList()
}
