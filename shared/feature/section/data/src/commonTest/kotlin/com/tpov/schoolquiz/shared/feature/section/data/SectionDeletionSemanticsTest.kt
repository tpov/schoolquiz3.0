package com.tpov.schoolquiz.shared.feature.section.data

import com.tpov.schoolquiz.shared.core.persistence.SectionEntity
import com.tpov.schoolquiz.shared.feature.section.data.dto.SectionDto
import com.tpov.schoolquiz.shared.feature.section.data.fake.FakeSectionLocalDataSource
import com.tpov.schoolquiz.shared.feature.section.data.fake.FakeSectionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Способов удалить узел ровно три, и здесь они проверяются поведением, а не текстом файла (AD-26).
 *
 * Секция не квест: полки «Архив» у неё нет, но `archived` всё равно означает «скрыть», а не
 * «стереть» — AC 7.6 требует, чтобы локальная строка и связанный с ней прогресс остались.
 */
class SectionDeletionSemanticsTest {

    private val local = FakeSectionLocalDataSource()
    private val remote = FakeSectionRemoteDataSource()
    private val repository = SectionRepositoryImpl(local, remote)

    private fun dto(id: String, archived: Boolean = false, version: Long = 2L) =
        SectionDto(id, "quest-1", "Секция $id", 0, version, 200L, archived)

    private fun entity(id: String, archived: Boolean = false, version: Long = 1L) =
        SectionEntity(id, "quest-1", "Секция $id", 0, version, lastModifiedAt = 100L, archived = archived)

    @Test
    fun `given an id the server did not return then its row goes away and the returned one stays`() = runTest {
        local.seed(listOf(entity("s-1"), entity("s-2")))
        remote.fetchByIdsResult = listOf(dto("s-1"))

        val result = repository.refreshByIds(setOf(SectionId("s-1"), SectionId("s-2")))

        assertTrue(result.isSuccess)
        assertEquals(listOf("s-2"), local.deletedIds)
        assertNotNull(local.findById("s-1"))
    }

    @Test
    fun `given an archived section then the row stays in the database and stops being shown`() = runTest {
        local.seed(listOf(entity("s-1")))
        remote.fetchByIdsResult = listOf(dto("s-1", archived = true))

        repository.refreshByIds(setOf(SectionId("s-1")))

        assertNotNull(local.findById("s-1"), "archived скрывает узел, а не удаляет его (AD-26)")
        assertEquals(emptyList(), local.deletedIds)
        assertEquals(emptyList(), repository.observeByQuest(QuestId("quest-1")).first())
    }

    @Test
    fun `given an archived section arriving by parents then the row stays too`() = runTest {
        local.seed(listOf(entity("s-1")))
        remote.result = listOf(dto("s-1", archived = true, version = 5L))

        val changed = repository.refreshByParents(setOf(QuestId("quest-1")), 0L)

        assertNotNull(local.findById("s-1"))
        assertEquals(emptyList(), local.deletedIds)
        assertEquals(emptySet(), changed.getOrNull(), "в детей скрытого узла спускаться незачем")
        assertEquals(emptyList(), repository.observeByQuest(QuestId("quest-1")).first())
    }
}
