package com.tpov.schoolquiz.shared.feature.quest.data.fake

import com.tpov.schoolquiz.shared.feature.quest.data.QuestRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto

/**
 * Fake QuestRemoteDataSource for integration tests.
 * By default throws if called — verifies offline tests don't touch the network.
 */
class FakeQuestRemoteDataSource(
    private val shouldThrow: Boolean = false,
    private val ownResults: List<QuestDto> = emptyList(),
    private val publicResults: List<QuestDto> = emptyList(),
) : QuestRemoteDataSource {

    var fetchOwnChangedCallCount: Int = 0
    var fetchPublicChangedCallCount: Int = 0

    override suspend fun fetchOwnChanged(
        authorUid: String,
        catalogIds: Set<String>,
        cursor: Long,
    ): List<QuestDto> {
        fetchOwnChangedCallCount++
        if (shouldThrow) throw RuntimeException("FakeQuestRemoteDataSource: unexpected fetchOwnChanged call")
        return ownResults
    }

    override suspend fun fetchPublicChanged(
        shelves: Set<String>,
        cursor: Long,
    ): List<QuestDto> {
        fetchPublicChangedCallCount++
        if (shouldThrow) throw RuntimeException("FakeQuestRemoteDataSource: unexpected fetchPublicChanged call")
        return publicResults
    }
}
