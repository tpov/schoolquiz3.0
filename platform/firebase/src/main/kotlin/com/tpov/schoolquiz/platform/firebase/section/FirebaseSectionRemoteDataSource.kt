package com.tpov.schoolquiz.platform.firebase.section

import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.catalog.toTimestamp
import com.tpov.schoolquiz.platform.firebase.util.fetchDocumentsByIds
import com.tpov.schoolquiz.shared.feature.section.data.SectionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.section.data.dto.SectionDto
import kotlinx.coroutines.tasks.await

class FirebaseSectionRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : SectionRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<SectionDto> {
        if (ids.isEmpty()) return emptyList()
        return firestore.fetchDocumentsByIds("sections", ids) { it.toSectionDto() }
    }

    override suspend fun fetchChangedByParents(
        questIds: Set<String>,
        cursor: Long,
    ): List<SectionDto> {
        if (questIds.isEmpty()) return emptyList()
        val ts = cursor.toTimestamp()
        return firestore.collection("sections")
            .whereIn("questId", questIds.toList())
            .whereGreaterThan("lastModifiedAt", ts)
            .get()
            .await()
            .documents
            .map { it.toSectionDto() }
    }
}
