package com.tpov.schoolquiz.platform.firebase.util

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private const val FIRESTORE_IN_QUERY_LIMIT = 10

internal suspend fun <T> FirebaseFirestore.fetchDocumentsByIds(
    collectionPath: String,
    ids: Set<String>,
    mapper: (DocumentSnapshot) -> T,
): List<T> {
    if (ids.isEmpty()) return emptyList()
    val collection = collection(collectionPath)
    return ids
        .toList()
        .chunked(FIRESTORE_IN_QUERY_LIMIT)
        .flatMap { chunk ->
            collection
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .await()
                .documents
                .map(mapper)
        }
}
