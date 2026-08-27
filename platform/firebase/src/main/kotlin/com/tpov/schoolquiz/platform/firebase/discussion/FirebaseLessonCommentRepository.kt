package com.tpov.schoolquiz.platform.firebase.discussion

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonComment
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonCommentRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseLessonCommentRepository(
    private val firestore: FirebaseFirestore,
) : LessonCommentRepository {
    override fun observe(lessonId: LessonId): Flow<List<LessonComment>> =
        callbackFlow {
            val registration =
                firestore
                    .collection(COLLECTION)
                    .whereEqualTo(FIELD_LESSON_ID, lessonId.value)
                    .orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
                    .addSnapshotListener { snapshot, _ ->
                        val comments =
                            snapshot
                                ?.documents
                                .orEmpty()
                                .map { doc -> doc.toComment() }
                        trySend(comments)
                    }
            awaitClose { registration.remove() }
        }

    override suspend fun post(
        lessonId: LessonId,
        authorNickname: String,
        authorAvatarUrl: String?,
        text: String,
    ): Result<Unit> =
        runCatching {
            firestore
                .collection(COLLECTION)
                .add(
                    mapOf(
                        FIELD_LESSON_ID to lessonId.value,
                        FIELD_AUTHOR_NICKNAME to authorNickname,
                        FIELD_AUTHOR_AVATAR to authorAvatarUrl,
                        FIELD_TEXT to text,
                        FIELD_CREATED_AT to System.currentTimeMillis(),
                    ),
                ).await()
            Unit
        }

    private fun com.google.firebase.firestore.DocumentSnapshot.toComment(): LessonComment =
        LessonComment(
            id = id,
            lessonId = LessonId(getString(FIELD_LESSON_ID).orEmpty()),
            authorNickname = getString(FIELD_AUTHOR_NICKNAME).orEmpty(),
            authorAvatarUrl = getString(FIELD_AUTHOR_AVATAR),
            text = getString(FIELD_TEXT).orEmpty(),
            createdAtMs = getLong(FIELD_CREATED_AT) ?: 0L,
        )

    private companion object {
        const val COLLECTION = "lessonComments"
        const val FIELD_LESSON_ID = "lessonId"
        const val FIELD_AUTHOR_NICKNAME = "authorNickname"
        const val FIELD_AUTHOR_AVATAR = "authorAvatarUrl"
        const val FIELD_TEXT = "text"
        const val FIELD_CREATED_AT = "createdAt"
    }
}
