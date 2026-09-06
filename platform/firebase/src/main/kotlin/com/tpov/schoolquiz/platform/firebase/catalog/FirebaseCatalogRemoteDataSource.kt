package com.tpov.schoolquiz.platform.firebase.catalog

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogDto
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import kotlinx.coroutines.tasks.await

private const val MILLIS_PER_SECOND = 1_000L
private const val NANOS_PER_MILLI = 1_000_000L

class FirebaseCatalogRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : CatalogRemoteDataSource {
    /**
     * Страница списка каталогов по паре `(lastModifiedAt, id)` (AD-31).
     *
     * Порядок и продолжение — те же, что у четырёх журналов: `startAfter` по паре, а при пустом
     * `docId` — `startAt` по времени, потому что «в этой миллисекунде ещё ничего не прочитано» —
     * это не «после документа с пустым именем». Прежде здесь стояло `whereGreaterThan` по одному
     * времени и не было `limit`: каталог, изменённый в ту же миллисекунду, что и курсор, терялся
     * навсегда, а вся коллекция вычитывалась одним ответом.
     */
    override suspend fun fetchChangedSince(
        cursor: SyncCursor,
        limit: Int,
    ): List<CatalogDto> {
        val ts = cursor.changedAtMs.toTimestamp()
        return firestore.collection("catalogs")
            .orderBy("lastModifiedAt")
            .orderBy(FieldPath.documentId())
            .let { if (cursor.docId.isEmpty()) it.startAt(ts) else it.startAfter(ts, cursor.docId) }
            .limit(limit.toLong())
            .get()
            .await()
            .documents
            .mapNotNull { it.toCatalogDto() }
    }

    override suspend fun fetchByIds(ids: Set<String>): List<CatalogDto> {
        if (ids.isEmpty()) return emptyList()
        return ids.mapNotNull { id ->
            firestore.collection("catalogs")
                .document(id)
                .get()
                .await()
                .takeIf { it.exists() }
                ?.toCatalogDto()
        }
    }
}

internal fun Long.toTimestamp(): Timestamp =
    Timestamp(this / MILLIS_PER_SECOND, ((this % MILLIS_PER_SECOND) * NANOS_PER_MILLI).toInt())
