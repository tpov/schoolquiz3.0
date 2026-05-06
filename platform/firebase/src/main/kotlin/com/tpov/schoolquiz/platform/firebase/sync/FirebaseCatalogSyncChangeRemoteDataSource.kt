package com.tpov.schoolquiz.platform.firebase.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.util.millisField
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChange
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncNodeType
import kotlinx.coroutines.tasks.await

class FirebaseCatalogSyncChangeRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : CatalogSyncChangeRemoteDataSource {
    override suspend fun fetchChangedSince(
        catalogId: CatalogId,
        cursorMs: Long,
    ): List<CatalogSyncChange> =
        firestore.collection("catalogs")
            .document(catalogId.value)
            .collection("sync_changes")
            .whereGreaterThan("changedAtMs", cursorMs)
            .orderBy("changedAtMs")
            .get()
            .await()
            .documents
            .mapNotNull { it.toCatalogSyncChange(catalogId) }
}

private fun DocumentSnapshot.toCatalogSyncChange(catalogId: CatalogId): CatalogSyncChange? {
    val type = getString("type").toSyncNodeType() ?: return null
    val nodeId = getString("id")?.takeIf { it.isNotBlank() } ?: return null
    val changedAtMs = millisField("changedAtMs") ?: return null
    return CatalogSyncChange(
        catalogId = catalogId,
        type = type,
        nodeId = nodeId,
        changedAtMs = changedAtMs,
    )
}

private fun String?.toSyncNodeType(): CatalogSyncNodeType? =
    when (this?.trim()?.lowercase()) {
        "catalog", "catalogs" -> CatalogSyncNodeType.Catalog
        "quest", "quests" -> CatalogSyncNodeType.Quest
        "section", "sections" -> CatalogSyncNodeType.Section
        "theme", "themes" -> CatalogSyncNodeType.Theme
        "lesson", "lessons" -> CatalogSyncNodeType.Lesson
        "question", "questions" -> CatalogSyncNodeType.Question
        else -> null
    }
