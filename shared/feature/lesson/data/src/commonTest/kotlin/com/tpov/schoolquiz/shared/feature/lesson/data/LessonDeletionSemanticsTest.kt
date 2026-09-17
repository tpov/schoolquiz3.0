package com.tpov.schoolquiz.shared.feature.lesson.data

import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto
import com.tpov.schoolquiz.shared.feature.lesson.data.fake.FakeLessonLocalDataSource
import com.tpov.schoolquiz.shared.feature.lesson.data.fake.FakeLessonRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Три способа удаления — поведением, а не текстом файла (AD-26, AC 7.6).
 *
 * У урока цена ошибки самая высокая: строка вопроса связана с уроком внешним ключом с
 * `ON DELETE CASCADE`, поэтому удаление урока уносило бы и все его вопросы.
 */
class LessonDeletionSemanticsTest {

    private val local = FakeLessonLocalDataSource()
    private val remote = FakeLessonRemoteDataSource()
    private val repository = LessonRepositoryImpl(local, remote)

    private fun dto(id: String, archived: Boolean = false, version: Long = 2L) =
        LessonDto(id, "theme-1", "Урок $id", 0, version, 200L, archived)

    private fun entity(id: String, archived: Boolean = false, order: Int = 0) =
        LessonEntity(id, "theme-1", "Урок $id", order, 1L, lastModifiedAt = 100L, archived = archived)

    @Test
    fun `given an id the server did not return then its row goes away and the returned one stays`() = runTest {
        local.seed(listOf(entity("l-1"), entity("l-2")))
        remote.fetchByIdsResult = listOf(dto("l-1"))

        val result = repository.refreshByIds(setOf(LessonId("l-1"), LessonId("l-2")))

        assertTrue(result.isSuccess)
        assertEquals(listOf("l-2"), local.deletedIds)
        assertNotNull(local.findById("l-1"))
    }

    @Test
    fun `given an archived lesson then the row stays in the database and stops being shown`() = runTest {
        local.seed(listOf(entity("l-1")))
        remote.fetchByIdsResult = listOf(dto("l-1", archived = true))

        repository.refreshByIds(setOf(LessonId("l-1")))

        assertNotNull(local.findById("l-1"), "иначе каскад FK унёс бы и все вопросы урока")
        assertEquals(emptyList(), local.deletedIds)
        assertEquals(emptyList(), repository.observeByTheme(ThemeId("theme-1")).first())
    }

    @Test
    fun `given an archived lesson then it is not counted as taught before the next one`() = runTest {
        local.seed(listOf(entity("l-1", archived = true, order = 0), entity("l-2", order = 1)))

        // Строка на месте — но в «чему уже учили» скрытый урок не попадает.
        assertNotNull(local.findById("l-1"))
        assertEquals(emptyList(), repository.titlesTaughtBefore(LessonId("l-2")))
    }
}
