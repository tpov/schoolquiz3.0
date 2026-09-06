package com.tpov.schoolquiz.platform.firebase.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.util.millisField
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChange
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncNodeType
import com.tpov.schoolquiz.shared.core.sync.JournalReadPolicy
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor

class FirebaseCatalogSyncChangeRemoteDataSource(
    private val firestore: FirebaseFirestore,
    /**
     * Один переключатель терпимости на все четыре журнала (AD-11). До backfill множественное
     * число типа разбирается, после — нет, и такая запись становится непонятой.
     */
    private val policy: JournalReadPolicy = JournalReadPolicy.TOLERANT,
) : CatalogSyncChangeRemoteDataSource {
    /** Страница журнала `catalogs/{catalogId}/sync_changes` (AD-31). См. [fetchSyncChangePage]. */
    override suspend fun fetchPage(
        catalogId: CatalogId,
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<CatalogSyncChange> =
        firestore.collection("catalogs")
            .document(catalogId.value)
            .collection("sync_changes")
            .fetchSyncChangePage(cursor, limit) { it.toCatalogSyncChange(catalogId, policy) }
}

private fun DocumentSnapshot.toCatalogSyncChange(
    catalogId: CatalogId,
    policy: JournalReadPolicy,
): CatalogSyncChange? {
    val type = getString("type").toSyncNodeType(policy)
    val nodeId = getString("id")?.takeIf { it.isNotBlank() }
    val changedAtMs = millisField(SYNC_CHANGE_CHANGED_AT_MS)
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

/**
 * Терпит и единственное, и множественное число, пока журналы не приведены к одной форме (AD-11).
 * Непонятое здесь — `null`: до приведения читатель его считает, после — прогон на нём падает, и
 * курсор остаётся на месте.
 */
internal fun String?.toSyncNodeType(policy: JournalReadPolicy): CatalogSyncNodeType? =
    when (val value = this?.trim()?.lowercase()) {
        "catalog" -> CatalogSyncNodeType.Catalog
        "quest" -> CatalogSyncNodeType.Quest
        "section" -> CatalogSyncNodeType.Section
        "theme" -> CatalogSyncNodeType.Theme
        "lesson" -> CatalogSyncNodeType.Lesson
        "question" -> CatalogSyncNodeType.Question
        else -> if (policy.strict) null else value.toLegacyPluralNodeType()
    }

/** Форма до backfill: тип во множественном числе. Снимается вместе с терпимостью. */
private fun String?.toLegacyPluralNodeType(): CatalogSyncNodeType? =
    when (this) {
        "catalogs" -> CatalogSyncNodeType.Catalog
        "quests" -> CatalogSyncNodeType.Quest
        "sections" -> CatalogSyncNodeType.Section
        "themes" -> CatalogSyncNodeType.Theme
        "lessons" -> CatalogSyncNodeType.Lesson
        "questions" -> CatalogSyncNodeType.Question
        else -> null
    }
