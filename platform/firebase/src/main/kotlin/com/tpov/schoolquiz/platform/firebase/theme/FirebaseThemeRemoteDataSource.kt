package com.tpov.schoolquiz.platform.firebase.theme

import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.catalog.toTimestamp
import com.tpov.schoolquiz.platform.firebase.util.fetchDocumentsByIds
import com.tpov.schoolquiz.shared.feature.theme.data.ThemeRemoteDataSource
import com.tpov.schoolquiz.shared.feature.theme.data.dto.ThemeDto
import kotlinx.coroutines.tasks.await

class FirebaseThemeRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : ThemeRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<ThemeDto> {
        if (ids.isEmpty()) return emptyList()
        return firestore.fetchDocumentsByIds("themes", ids) { it.toThemeDto() }
    }

    override suspend fun fetchChangedByParents(
        sectionIds: Set<String>,
        cursor: Long,
    ): List<ThemeDto> {
        if (sectionIds.isEmpty()) return emptyList()
        val ts = cursor.toTimestamp()
        return firestore.collection("themes")
            .whereIn("sectionId", sectionIds.toList())
            .whereGreaterThan("lastModifiedAt", ts)
            .get()
            .await()
            .documents
            .map { it.toThemeDto() }
    }
}
