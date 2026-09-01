package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestSubmissionOutcome
import kotlinx.coroutines.tasks.await

/**
 * Только чтение вердиктов.
 *
 * Прямая запись заявки в `quest_review_requests` снята: отложенная мутация уходит единственным
 * приёмником (AD-6), потому что ключ идемпотентности проверяется там, где выполняется код, — а
 * правило Firestore кода не выполняет и вторую доставку той же заявки отличить не может.
 */
class FirebaseQuestArenaSubmissionRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : QuestArenaSubmissionRemoteDataSource {
    override suspend fun fetchOutcomes(ownerUid: String): List<QuestSubmissionOutcome> =
        firestore.collection(REVIEW_REQUESTS_COLLECTION)
            .whereEqualTo("ownerUid", ownerUid)
            .whereIn("status", listOf(STATUS_REJECTED, STATUS_PUBLISHED))
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val draftId = doc.getString("draftId") ?: return@mapNotNull null
                QuestSubmissionOutcome(
                    submissionId = doc.id,
                    draftId = draftId,
                    status = doc.getString("status").orEmpty(),
                    rejectionReason = doc.getString("rejectionReason"),
                )
            }

    private companion object {
        const val REVIEW_REQUESTS_COLLECTION = "quest_review_requests"
        const val STATUS_REJECTED = QuestSubmissionOutcome.STATUS_REJECTED
        const val STATUS_PUBLISHED = QuestSubmissionOutcome.STATUS_PUBLISHED
    }
}
