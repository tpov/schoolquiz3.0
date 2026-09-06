package com.tpov.schoolquiz.shared.feature.quest.data

import com.tpov.schoolquiz.shared.core.catalog.data.StorageUrlResolver
import com.tpov.schoolquiz.shared.core.persistence.QuestEntity
import com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto
import com.tpov.schoolquiz.shared.feature.quest.data.fake.FakeOutboxStore
import com.tpov.schoolquiz.shared.feature.quest.data.fake.FakeQuestLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest.data.fake.FakeQuestRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Три способа удаления — поведением, а не текстом файла (AD-26, AC 7.6).
 *
 * Квест — единственный узел, у которого `archived` остаётся маркером: у него есть полка «Архив»,
 * и открыть его оттуда по требованию надо уметь. Второй способ — пустой `visibleOn` — есть тоже
 * только у него: полки есть лишь у квеста.
 */
class QuestDeletionSemanticsTest {

    private val local = FakeQuestLocalDataSource()

    private fun dto(
        id: String,
        version: Long = 2L,
        archived: Boolean = false,
        visibleOn: List<String> = listOf("home"),
    ) = QuestDto(id, "c1", "uid-a", "Квест $id", null, visibleOn, null, 0, version, 200L, archived)

    private fun entity(id: String) =
        QuestEntity(
            id = id,
            catalogId = "c1",
            authorUid = "uid-a",
            title = "Квест $id",
            picturePath = null,
            pictureUrl = null,
            visibleOn = setOf("home"),
            averageRating = null,
            averageRatingCount = 0,
            version = 1L,
            lastModifiedAt = 100L,
            archived = false,
        )

    private fun repository(remote: FakeQuestRemoteDataSource) =
        QuestRepositoryImpl(
            local = local,
            remote = remote,
            storageUrlResolver = StorageUrlResolver { "https://fake.example.com/$it" },
            outbox = FakeOutboxStore(),
        )

    @Test
    fun `given an id the server did not return then its row goes away and the returned one stays`() = runTest {
        local.seed(listOf(entity("q-1"), entity("q-2")))
        val repository = repository(FakeQuestRemoteDataSource(byIdResults = listOf(dto("q-1"))))

        val result = repository.refreshByIds(setOf(QuestId("q-1"), QuestId("q-2")))

        assertTrue(result.isSuccess)
        assertEquals(listOf("q-2"), local.deletedIds)
        assertNotNull(local.findById("q-1"))
    }

    @Test
    fun `given an empty shelf list arriving through the journal then the quest leaves the device`() = runTest {
        // Ветка `fromSyncList = true` — та, по которой ходит журнал; прежде поведенческого теста
        // у неё не было вовсе, проверялся только путь refreshFromRemote.
        local.seed(listOf(entity("q-1")))
        val repository = repository(FakeQuestRemoteDataSource(byIdResults = listOf(dto("q-1", visibleOn = emptyList()))))

        repository.refreshByIds(setOf(QuestId("q-1")))

        assertTrue(local.deletedIds.contains("q-1"))
        assertNull(local.findById("q-1"))
    }

    @Test
    fun `given an empty shelf list with a version no newer then the journal still removes it`() = runTest {
        // По журналу квест снимается независимо от версии: журнал уже сказал, что узел тронули.
        local.seed(listOf(entity("q-1")))
        val repository =
            repository(FakeQuestRemoteDataSource(byIdResults = listOf(dto("q-1", version = 1L, visibleOn = emptyList()))))

        repository.refreshByIds(setOf(QuestId("q-1")))

        assertNull(local.findById("q-1"))
    }

    @Test
    fun `given an archived quest then it stays on the device as the Archive shelf marker`() = runTest {
        local.seed(listOf(entity("q-1")))
        val repository = repository(FakeQuestRemoteDataSource(byIdResults = listOf(dto("q-1", archived = true))))

        repository.refreshByIds(setOf(QuestId("q-1")))

        assertEquals(emptyList(), local.deletedIds)
        assertTrue(local.findById("q-1")?.archived == true)
        // Полка у него остаётся: «Архив» — это отдельная выборка по archived = 1, а не отсутствие.
        assertEquals(listOf("q-1"), repository.observeByShelf("home").first().map { it.id.value })
    }
}
