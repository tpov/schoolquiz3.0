package com.tpov.schoolquiz.platform.firebase.quest

import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.catalog.toTimestamp
import com.tpov.schoolquiz.shared.feature.quest.data.QuestRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto
import kotlinx.coroutines.tasks.await

class FirebaseQuestRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : QuestRemoteDataSource {

    override suspend fun fetchOwnChanged(
        authorUid: String,
        catalogIds: Set<String>,
        cursor: Long,
    ): List<QuestDto> {
        if (catalogIds.isEmpty()) return emptyList()
        val ts = cursor.toTimestamp()
        return firestore.collection("quests")
            .whereEqualTo("authorUid", authorUid)
            .whereIn("catalogId", catalogIds.toList())
            .whereGreaterThan("lastModifiedAt", ts)
            .get()
            .await()
            .documents
            .map { it.toQuestDto() }
    }

    override suspend fun fetchPublicChanged(
        shelves: Set<String>,
        cursor: Long,
    ): List<QuestDto> {
        if (shelves.isEmpty()) return emptyList()
        val ts = cursor.toTimestamp()
        return firestore.collection("quests")
            .whereArrayContainsAny("visibleOn", shelves.toList())
            .whereGreaterThan("lastModifiedAt", ts)
            .get()
            .await()
            .documents
            .map { it.toQuestDto() }
    }
}
