package com.tpov.schoolquiz.platform.firebase.catalog

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogDto
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogRemoteDataSource
import kotlinx.coroutines.tasks.await

private const val MILLIS_PER_SECOND = 1_000L
private const val NANOS_PER_MILLI = 1_000_000L

class FirebaseCatalogRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : CatalogRemoteDataSource {
    override suspend fun fetchChangedSince(cursor: Long): List<CatalogDto> {
        val ts = cursor.toTimestamp()
        return firestore.collection("catalogs")
            .whereGreaterThan("lastModifiedAt", ts)
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
