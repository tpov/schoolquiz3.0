package com.tpov.schoolquiz.platform.firebase.question

import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.catalog.toTimestamp
import com.tpov.schoolquiz.shared.feature.question.data.QuestionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.question.data.dto.QuestionDto
import kotlinx.coroutines.tasks.await

class FirebaseQuestionRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : QuestionRemoteDataSource {

    override suspend fun fetchChangedByParents(lessonIds: Set<String>, cursor: Long): List<QuestionDto> {
        if (lessonIds.isEmpty()) return emptyList()
        val ts = cursor.toTimestamp()
        return firestore.collection("questions")
            .whereIn("lessonId", lessonIds.toList())
            .whereGreaterThan("lastModifiedAt", ts)
            .get()
            .await()
            .documents
            .map { it.toQuestionDto() }
    }
}
