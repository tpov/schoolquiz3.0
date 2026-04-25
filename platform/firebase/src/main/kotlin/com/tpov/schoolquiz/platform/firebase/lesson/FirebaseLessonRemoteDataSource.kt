package com.tpov.schoolquiz.platform.firebase.lesson

import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.catalog.toTimestamp
import com.tpov.schoolquiz.shared.feature.lesson.data.LessonRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto
import kotlinx.coroutines.tasks.await

class FirebaseLessonRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : LessonRemoteDataSource {

    override suspend fun fetchChangedByParents(themeIds: Set<String>, cursor: Long): List<LessonDto> {
        if (themeIds.isEmpty()) return emptyList()
        val ts = cursor.toTimestamp()
        return firestore.collection("lessons")
            .whereIn("themeId", themeIds.toList())
            .whereGreaterThan("lastModifiedAt", ts)
            .get()
            .await()
            .documents
            .map { it.toLessonDto() }
    }
}
