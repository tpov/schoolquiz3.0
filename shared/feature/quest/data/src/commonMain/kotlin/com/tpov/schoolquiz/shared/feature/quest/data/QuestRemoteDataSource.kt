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

    /** Developer-only callable: sets `quests/{questId}.visibleOn` to exactly [targetShelf]. */
    suspend fun setPublicShelf(
        questId: String,
        targetShelf: String,
    ) = Unit

    /** Developer-only callable: empties `quests/{questId}.visibleOn`, taking it off every shelf. */
    suspend fun retirePublic(questId: String) = Unit
}
