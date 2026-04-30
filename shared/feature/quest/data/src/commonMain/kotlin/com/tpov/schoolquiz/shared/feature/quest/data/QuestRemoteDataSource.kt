package com.tpov.schoolquiz.shared.feature.quest.data

import com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto

interface QuestRemoteDataSource {
    /** Fetch concrete quests by ids from the flat `quests` collection. */
    suspend fun fetchByIds(ids: Set<String>): List<QuestDto> = emptyList()

    /** Query A: own quests by authorUid + catalogIds. catalogIds.size ≤ 30 (Firestore in-filter limit). */
    suspend fun fetchOwnChanged(
        authorUid: String,
        catalogIds: Set<String>,
        cursor: Long,
    ): List<QuestDto>

    /** Query B: public quests by visibleOn shelves. */
    suspend fun fetchPublicChanged(
        shelves: Set<String>,
        cursor: Long,
    ): List<QuestDto>
}
