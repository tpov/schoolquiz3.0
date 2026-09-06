package com.tpov.schoolquiz.shared.feature.quest.data

import com.tpov.schoolquiz.shared.core.catalog.data.StorageUrlResolver
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.QuestEntity
import com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto
import com.tpov.schoolquiz.shared.feature.quest.data.fake.FakeOutboxStore
import com.tpov.schoolquiz.shared.feature.quest.data.fake.FakeQuestLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest.data.fake.FakeQuestRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest.data.outbox.QuestShelfOutbox
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestShelfChange
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestShelfSyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Перенос квеста на полку кладёт в очередь намерение, а не полку (история 5.4).
 *
 * Локальная половина пуста (AD-25): строка квеста не трогается ни при постановке, ни при
 * карантине. Новая полка приезжает журналом — тем же путём, каким приезжает всё серверное.
 */
class QuestRepositoryImplShelfChangeTest {

    private val local = FakeQuestLocalDataSource()
    private val remote = FakeQuestRemoteDataSource(shouldThrow = true)
    private val outbox = FakeOutboxStore()
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(NOW_MS)
        }
    private val repository =
        QuestRepositoryImpl(
            local = local,
            remote = remote,
            storageUrlResolver = StorageUrlResolver { "https://fake.example.com/$it" },
            outbox = outbox,
            clock = clock,
        )

    private val questId = QuestId("q-1")

    private fun entity(visibleOn: Set<String> = setOf("home"), version: Long = 1L) =
        QuestEntity(
            id = questId.value,
            catalogId = "cat-1",
            authorUid = OWNER,
            title = "Quest",
            picturePath = null,
            pictureUrl = null,
            visibleOn = visibleOn,
            averageRating = null,
            averageRatingCount = 0,
            version = version,
            lastModifiedAt = 10L,
            archived = false,
        )

    private fun change(id: String = "intent-1", target: String = "arena") =
        QuestShelfChange(id = id, ownerUid = OWNER, questId = questId, targetShelf = target)

    @Test
    fun `given airplane mode when a move is queued then the row carries the intent and visibleOn is untouched`() =
        runTest {
            local.seed(listOf(entity()))

            val result = repository.queueShelfChange(change())

            assertTrue(result.isSuccess)
            val row = outbox.all.single()
            assertEquals(QuestShelfOutbox.OPERATION, row.operation)
            assertEquals("quest.SET_SHELF", row.operation)
            assertEquals("quest.QUEST:q-1", row.entityRef)
            assertEquals(OWNER, row.ownerUid)
            assertEquals(OutboxState.WAITING, row.state)
            assertEquals(NOW_MS, row.createdAtMs)
            assertEquals(null, row.expectedVersion)
            assertEquals("quest-SET_SHELF-intent-1", row.mutationId)
            assertEquals("""{"questId":"q-1","targetShelf":"arena"}""", row.payload)
            // Строка квеста не изменилась: полка серверно-защищена (AD-25).
            assertEquals(setOf("home"), local.findById(questId.value)?.visibleOn)
            assertTrue(local.upsertCallsFor.isEmpty(), "локальной половины у переноса нет")
            assertEquals(0, remote.fetchOwnChangedCallCount + remote.fetchPublicChangedCallCount)
        }

    @Test
    fun `given a pending move for the same quest when a newer one is queued then the older one is withdrawn`() =
        runTest {
            repository.queueShelfChange(change(id = "intent-1", target = "arena"))
            repository.queueShelfChange(change(id = "intent-2", target = "archive"))

            val row = outbox.all.single()
            assertEquals("quest-SET_SHELF-intent-2", row.mutationId)
            assertTrue(row.payload.contains("\"archive\""))
            assertEquals(listOf(1L), outbox.removed, "прежнее намерение снято (AD-2), а не оставлено ехать вторым")
        }

    @Test
    fun `given a quarantined move when a new one is queued then the quarantined row is withdrawn with it`() =
        runTest {
            repository.queueShelfChange(change(id = "intent-1"))
            outbox.quarantine(outbox.all.single().id)

            repository.queueShelfChange(change(id = "intent-2"))

            val row = outbox.all.single()
            assertEquals(OutboxState.WAITING, row.state)
            assertEquals("quest-SET_SHELF-intent-2", row.mutationId)
            assertEquals(listOf(1L), outbox.removed, "новое нажатие снимает пометку карантина (AD-28), а не кладёт рядом")
            assertEquals(QuestShelfSyncState.PENDING, repository.observeShelfChangeState(OWNER, questId).first())
            assertEquals(0, outbox.observeCounts(OWNER).first().quarantined)
        }

    @Test
    fun `given a quarantined move and a delivered newer one then the old mark does not come back`() =
        runTest {
            repository.queueShelfChange(change(id = "intent-1"))
            outbox.quarantine(outbox.all.single().id)
            repository.queueShelfChange(change(id = "intent-2"))

            // Новое доехало и удалено (AD-4). Пока карантинная запись оставалась лежать, самой
            // свежей по ссылке снова становилась она — и карточка показывала отказ, которого нет.
            outbox.remove(outbox.all.single().id)

            assertEquals(QuestShelfSyncState.NONE, repository.observeShelfChangeState(OWNER, questId).first())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `given two taps racing when both queue a move then only the last one is left`() =
        runTest {
            outbox.yieldBeforeFind = true

            launch { repository.queueShelfChange(change(id = "intent-1", target = "arena")) }
            launch { repository.queueShelfChange(change(id = "intent-2", target = "archive")) }
            advanceUntilIdle()

            assertEquals(1, outbox.all.size, "два быстрых нажатия — одна запись, не две едущие")
            assertEquals("quest-SET_SHELF-intent-2", outbox.all.single().mutationId, "последнее нажатие главнее")
        }

    @Test
    fun `entity ref answers none pending and quarantined as distinct values`() = runTest {
        assertEquals(QuestShelfSyncState.NONE, repository.observeShelfChangeState(OWNER, questId).first())

        repository.queueShelfChange(change())
        assertEquals(QuestShelfSyncState.PENDING, repository.observeShelfChangeState(OWNER, questId).first())
        assertEquals(
            QuestShelfSyncState.NONE,
            repository.observeShelfChangeState("someone-else", questId).first(),
            "чужая запись не видна под другим uid",
        )

        outbox.quarantine(outbox.all.single().id)
        assertEquals(QuestShelfSyncState.QUARANTINED, repository.observeShelfChangeState(OWNER, questId).first())
    }

    @Test
    fun `given the move was delivered when the journal refresh arrives then the new shelf lands through the row`() =
        runTest {
            local.seed(listOf(entity(visibleOn = setOf("home"), version = 1L)))
            repository.queueShelfChange(change(target = "arena"))
            // До доставки ни один снимок строки не показывает квест на новой полке.
            assertEquals(setOf("home"), local.findById(questId.value)?.visibleOn)

            // Доставлено: движок удалил запись (AD-4); журнал назвал квест изменённым.
            outbox.remove(outbox.all.single().id)
            val delivered =
                QuestRepositoryImpl(
                    local = local,
                    remote =
                        FakeQuestRemoteDataSource(
                            byIdResults = listOf(QuestDto(questId.value, "cat-1", OWNER, "Quest", null, listOf("arena"), null, 0, 2L, 20L, false)),
                        ),
                    storageUrlResolver = StorageUrlResolver { "https://fake.example.com/$it" },
                    outbox = outbox,
                    clock = clock,
                )
            val refreshed = delivered.refreshByIds(setOf(questId))

            assertTrue(refreshed.isSuccess)
            assertEquals(setOf("arena"), local.findById(questId.value)?.visibleOn)
            assertEquals(QuestShelfSyncState.NONE, delivered.observeShelfChangeState(OWNER, questId).first())
        }

    private companion object {
        const val OWNER = "uid-dev"
        const val NOW_MS = 1_700_000_000_000L
    }
}
