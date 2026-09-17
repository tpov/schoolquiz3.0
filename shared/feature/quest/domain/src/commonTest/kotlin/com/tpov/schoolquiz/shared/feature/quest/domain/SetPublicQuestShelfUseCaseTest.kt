package com.tpov.schoolquiz.shared.feature.quest.domain

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.fake.FakeQuestRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestShelfSyncState
import com.tpov.schoolquiz.shared.feature.quest.domain.provider.QuestShelfChangeIdProvider
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SetPublicQuestShelfUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Перенос квеста на полку — намерение в очереди, а не полка в строке (история 5.4, AD-25).
 */
class SetPublicQuestShelfUseCaseTest {

    private val questId = QuestId("q-1")
    private val quest =
        Quest(
            id = questId,
            catalogId = CatalogId("cat-1"),
            authorUid = "uid-dev",
            title = "Quest",
            picturePath = null,
            visibleOn = setOf("home"),
            version = 1L,
            lastModifiedAt = 10L,
        )

    @Test
    fun `given a valid shelf then the intent is queued and the local row keeps its shelf`() = runTest {
        val repository = FakeQuestRepository(initial = listOf(quest))
        val useCase = SetPublicQuestShelfUseCase(repository, QuestShelfChangeIdProvider { "intent-1" })

        val result = useCase.execute(ownerUid = "uid-dev", questId = questId, targetShelf = "arena")

        assertTrue(result.isSuccess)
        val queued = repository.queuedShelfChanges.single()
        assertEquals("intent-1", queued.id)
        assertEquals("uid-dev", queued.ownerUid)
        assertEquals(questId, queued.questId)
        assertEquals("arena", queued.targetShelf)
        // Полка серверно-защищена: локально она не меняется, пока не приедет журналом.
        assertEquals(setOf("home"), repository.snapshot().single().visibleOn)
        assertEquals(QuestShelfSyncState.PENDING, repository.observeShelfChangeState("uid-dev", questId).first())
    }

    @Test
    fun `given an unknown shelf then nothing is queued`() = runTest {
        val repository = FakeQuestRepository(initial = listOf(quest))
        val useCase = SetPublicQuestShelfUseCase(repository, QuestShelfChangeIdProvider { "intent-1" })

        val result = useCase.execute(ownerUid = "uid-dev", questId = questId, targetShelf = "basement")

        assertTrue(result.isFailure)
        assertTrue(repository.queuedShelfChanges.isEmpty())
    }

    @Test
    fun `given the same quest moved twice then each move is its own intent with its own key`() = runTest {
        val repository = FakeQuestRepository(initial = listOf(quest))
        val useCase = SetPublicQuestShelfUseCase(repository)

        useCase.execute(ownerUid = "uid-dev", questId = questId, targetShelf = "arena")
        useCase.execute(ownerUid = "uid-dev", questId = questId, targetShelf = "arena")

        val (first, second) = repository.queuedShelfChanges
        assertNotEquals(first.id, second.id, "два нажатия — два намерения (AD-2)")
    }
}
