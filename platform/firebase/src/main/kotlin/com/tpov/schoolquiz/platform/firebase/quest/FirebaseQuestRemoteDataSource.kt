package com.tpov.schoolquiz.platform.firebase.quest

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.catalog.toTimestamp
import com.tpov.schoolquiz.platform.firebase.util.fetchDocumentsByIds
import com.tpov.schoolquiz.shared.feature.quest.data.QuestRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto
import kotlinx.coroutines.tasks.await

class FirebaseQuestRemoteDataSource(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : QuestRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<QuestDto> {
        if (ids.isEmpty()) return emptyList()
        return firestore.fetchDocumentsByIds("quests", ids) { it.toQuestDto() }
    }

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

    override suspend fun setPublicShelf(
        questId: String,
        targetShelf: String,
    ) {
        functions
            .getHttpsCallable(SET_PUBLIC_QUEST_SHELF)
            .call(
                mapOf(
                    "questId" to questId,
                    "targetShelf" to targetShelf,
                ),
            )
            .await()
    }

    override suspend fun retirePublic(questId: String) {
        functions
            .getHttpsCallable(RETIRE_PUBLIC_QUEST)
            .call(mapOf("questId" to questId))
            .await()
    }

    private companion object {
        const val SET_PUBLIC_QUEST_SHELF = "setPublicQuestShelf"
        const val RETIRE_PUBLIC_QUEST = "retirePublicQuest"
    }
}
