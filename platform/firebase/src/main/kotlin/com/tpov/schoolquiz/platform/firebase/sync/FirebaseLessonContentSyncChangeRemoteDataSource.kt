package com.tpov.schoolquiz.platform.firebase.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncNodeType
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncChange
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import kotlinx.coroutines.tasks.await

class FirebaseLessonContentSyncChangeRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : LessonContentSyncChangeRemoteDataSource {
    override suspend fun fetchChangedSince(
        lessonId: LessonId,
        cursorMs: Long,
    ): List<LessonContentSyncChange> =
        firestore.collection("lesson_content")
            .document(lessonId.value)
            .collection("sync_changes")
            .whereGreaterThan("changedAtMs", cursorMs)
            .orderBy("changedAtMs")
            .get()
            .await()
            .documents
            .mapNotNull { it.toLessonContentSyncChange(lessonId) }
}

private fun DocumentSnapshot.toLessonContentSyncChange(lessonId: LessonId): LessonContentSyncChange? {
    val type = getString("type").toSyncNodeType() ?: return null
    val nodeId = getString("id")?.takeIf { it.isNotBlank() } ?: return null
    val changedAtMs =
        getLong("changedAtMs")
            ?: getTimestamp("changedAtMs")?.toDate()?.time
            ?: return null
    return LessonContentSyncChange(
        lessonId = lessonId,
        type = type,
        nodeId = nodeId,
        changedAtMs = changedAtMs,
    )
}

private fun String?.toSyncNodeType(): CatalogSyncNodeType? =
    when (this?.trim()?.lowercase()) {
        "question", "questions" -> CatalogSyncNodeType.Question
        else -> null
    }
