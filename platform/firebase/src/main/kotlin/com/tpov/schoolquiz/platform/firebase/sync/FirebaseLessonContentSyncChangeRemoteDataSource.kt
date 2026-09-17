package com.tpov.schoolquiz.platform.firebase.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.util.millisField
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncNodeType
import com.tpov.schoolquiz.shared.core.sync.JournalReadPolicy
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncChange
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId

class FirebaseLessonContentSyncChangeRemoteDataSource(
    private val firestore: FirebaseFirestore,
    /** Тот же переключатель терпимости, что у остальных трёх журналов (AD-11). */
    private val policy: JournalReadPolicy = JournalReadPolicy.TOLERANT,
) : LessonContentSyncChangeRemoteDataSource {
    /** Страница журнала `lesson_content/{lessonId}/sync_changes` (AD-31). См. [fetchSyncChangePage]. */
    override suspend fun fetchPage(
        lessonId: LessonId,
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<LessonContentSyncChange> =
        firestore.collection("lesson_content")
            .document(lessonId.value)
            .collection("sync_changes")
            .fetchSyncChangePage(cursor, limit) { it.toLessonContentSyncChange(lessonId, policy) }
}

private fun DocumentSnapshot.toLessonContentSyncChange(
    lessonId: LessonId,
    policy: JournalReadPolicy,
): LessonContentSyncChange? {
    val type = getString("type").toLessonNodeType(policy)
    val nodeId = getString("id")?.takeIf { it.isNotBlank() }
    val changedAtMs = millisField(SYNC_CHANGE_CHANGED_AT_MS)
    return if (type != null && nodeId != null && changedAtMs != null) {
        LessonContentSyncChange(
            lessonId = lessonId,
            type = type,
            nodeId = nodeId,
            changedAtMs = changedAtMs,
        )
    } else {
        null
    }
}

/** В журнале урока лежат только вопросы. `questions` — форма до backfill (AD-11). */
private fun String?.toLessonNodeType(policy: JournalReadPolicy): CatalogSyncNodeType? =
    when (this?.trim()?.lowercase()) {
        "question" -> CatalogSyncNodeType.Question
        "questions" -> if (policy.strict) null else CatalogSyncNodeType.Question
        else -> null
    }
