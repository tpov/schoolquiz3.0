package com.tpov.schoolquiz.shared.feature.quest.data

import com.tpov.schoolquiz.shared.core.catalog.data.StorageUrlResolver
import com.tpov.schoolquiz.shared.feature.quest.data.fake.FakeQuestLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest.data.fake.FakeQuestRemoteDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Journey 3 integration test — offline-first empty state.
 * Room empty + no network → observeMyQuests emits emptyList (no crash).
 * AC#31 (offline-first).
 * Source: docs/features/home-and-my-quests/plan/phase-02/tests.md §8
 *
 * NOTE: Depends on backend-dev phase-02 task #6 (quest/data module creation).
 * This file compiles once QuestRepositoryImpl, QuestLocalDataSource,
 * QuestRemoteDataSource, and QuestDto exist in shared/feature/quest/data.
 */
class MyQuestsOfflineEmptyIntegrationTest {

    private val fakeLocal = FakeQuestLocalDataSource()
    private val fakeRemote = FakeQuestRemoteDataSource(shouldThrow = true)
    private val noOpResolver: StorageUrlResolver = StorageUrlResolver { path -> "https://fake.example.com/$path" }

    private val repository = QuestRepositoryImpl(
        local = fakeLocal,
        remote = fakeRemote,
        storageUrlResolver = noOpResolver,
    )

    // Journey 3 / AC#31: empty Room + offline → observeMyQuests emits empty list, no crash
    @Test
    fun `when room empty then observeMyQuests emits empty list`() = runTest {
        val quests = repository.observeMyQuests("uid-a", null).first()

        assertTrue(quests.isEmpty(), "Expected emptyList when local store has no quests")
        // Remote must NOT have been called — this is a read-only observe operation
        assertTrue(fakeRemote.fetchOwnChangedCallCount == 0, "Expected remote not called for observe")
        assertTrue(fakeRemote.fetchPublicChangedCallCount == 0, "Expected remote not called for observe")
    }

    // Journey 3 variant: filtering by catalogId on empty store also returns empty
    @Test
    fun `when room empty and catalogId filter applied then observeMyQuests emits empty list`() = runTest {
        val quests = repository.observeMyQuests("uid-a", com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId("cat-1")).first()

        assertTrue(quests.isEmpty(), "Expected emptyList when filtering empty store by catalogId")
    }
}
