package com.tpov.schoolquiz.shared.feature.theme.data

import com.tpov.schoolquiz.shared.core.persistence.ThemeEntity
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.data.dto.ThemeDto
import com.tpov.schoolquiz.shared.feature.theme.data.fake.FakeThemeLocalDataSource
import com.tpov.schoolquiz.shared.feature.theme.data.fake.FakeThemeRemoteDataSource
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Три способа удаления — поведением, а не текстом файла (AD-26, AC 7.6). */
class ThemeDeletionSemanticsTest {

    private val local = FakeThemeLocalDataSource()
    private val remote = FakeThemeRemoteDataSource()
    private val repository = ThemeRepositoryImpl(local, remote)

    private fun dto(id: String, archived: Boolean = false, version: Long = 2L) =
        ThemeDto(id, "section-1", "Тема $id", 0, version, 200L, archived)

    private fun entity(id: String, archived: Boolean = false) =
        ThemeEntity(id, "section-1", "Тема $id", 0, 1L, lastModifiedAt = 100L, archived = archived)

    @Test
    fun `given an id the server did not return then its row goes away and the returned one stays`() = runTest {
        local.seed(listOf(entity("t-1"), entity("t-2")))
        remote.fetchByIdsResult = listOf(dto("t-1"))

        val result = repository.refreshByIds(setOf(ThemeId("t-1"), ThemeId("t-2")))

        assertTrue(result.isSuccess)
        assertEquals(listOf("t-2"), local.deletedIds)
        assertNotNull(local.findById("t-1"))
    }

    @Test
    fun `given an archived theme then the row stays in the database and stops being shown`() = runTest {
        local.seed(listOf(entity("t-1")))
        remote.fetchByIdsResult = listOf(dto("t-1", archived = true))

        repository.refreshByIds(setOf(ThemeId("t-1")))

        assertNotNull(local.findById("t-1"), "archived скрывает узел, а не удаляет его (AD-26)")
        assertEquals(emptyList(), local.deletedIds)
        assertEquals(emptyList(), repository.observeBySection(SectionId("section-1")).first())
    }
}
