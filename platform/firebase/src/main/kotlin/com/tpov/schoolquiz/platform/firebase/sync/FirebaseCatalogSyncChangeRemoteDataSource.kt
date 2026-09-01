package com.tpov.schoolquiz.platform.firebase.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.util.millisField
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChange
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncNodeType
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
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

    /**
     * Страница журнала (AD-31).
     *
     * Порядок задаёт пара «время, id документа», и продолжение идёт через `startAfter` по той же
     * паре. Одного времени мало: две сущности, изменённые одним пакетом, получают одну
     * миллисекунду, и строгое сравнение по времени потеряло бы вторую навсегда.
     */
    override suspend fun fetchPage(
        catalogId: CatalogId,
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<CatalogSyncChange> {
        val documents =
            firestore.collection("catalogs")
                .document(catalogId.value)
                .collection("sync_changes")
                .orderBy("changedAtMs")
                .orderBy(FieldPath.documentId())
                // Пустой docId — это не «начать после документа с пустым именем», а «в этой
                // миллисекунде ещё ничего не прочитано». Отдать его в startAfter нельзя: Firestore
                // достраивает путь документа из этого значения и на пустой строке падает с
                // IllegalArgumentException, то есть каталог не синхронизировался бы вовсе.
                //
                // startAt перечитает всю миллисекунду курсора заново. Это дешевле, чем потерять
                // запись: применение изменений идёт через refreshByIds и повтор безвреден.
                .let { if (cursor.docId.isEmpty()) it.startAt(cursor.changedAtMs) else it.startAfter(cursor.changedAtMs, cursor.docId) }
                .limit(limit.toLong())
                .get()
                .await()
                .documents

        val changes = documents.mapNotNull { it.toCatalogSyncChange(catalogId) }
        if (documents.isEmpty()) return SyncChangePage.empty()
        // Курсор двигается по последнему ПРОЧИТАННОМУ документу, а не по последней распознанной
        // записи: иначе битая запись в конце страницы читалась бы вечно.
        val last = documents.last()
        return SyncChangePage(
            changes = changes,
            nextCursor = SyncCursor(last.millisField("changedAtMs") ?: cursor.changedAtMs, last.id),
            hasMore = documents.size >= limit,
        )
    }
}

private fun DocumentSnapshot.toCatalogSyncChange(catalogId: CatalogId): CatalogSyncChange? {
    val type = getString("type").toSyncNodeType()
    val nodeId = getString("id")?.takeIf { it.isNotBlank() }
    val changedAtMs = millisField("changedAtMs")
    return if (type != null && nodeId != null && changedAtMs != null) {
        CatalogSyncChange(
            catalogId = catalogId,
            type = type,
            nodeId = nodeId,
            changedAtMs = changedAtMs,
        )
    } else {
        null
    }
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
