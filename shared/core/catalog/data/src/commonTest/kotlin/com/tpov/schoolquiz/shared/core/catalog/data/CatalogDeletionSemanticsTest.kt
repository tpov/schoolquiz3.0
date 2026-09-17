package com.tpov.schoolquiz.shared.core.catalog.data

import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogLocalDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogUrlResolver
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeSyncStateRepository
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Три способа удаления — поведением, а не текстом файла (AD-26, AC 7.6). */
class CatalogDeletionSemanticsTest {

    private val local = FakeCatalogLocalDataSource()
    private val remote = FakeCatalogRemoteDataSource()
    private val repository =
        CatalogRepositoryImpl(
            local = local,
            remote = remote,
            storageUrlResolver = FakeCatalogUrlResolver().resolver,
            syncStateRepo = FakeSyncStateRepository(),
        )

    private fun dto(id: String, archived: Boolean = false, version: Long = 2L) =
        CatalogDto(id, "Каталог $id", picturePath = null, version = version, lastModifiedAt = 200L, archived = archived)

    private fun entity(id: String) =
        CatalogEntity(id, "Каталог $id", picturePath = null, pictureUrl = null, version = 1L, lastModifiedAt = 100L)

    @Test
    fun `given an id the server did not return then its row goes away and the returned one stays`() = runTest {
        local.seed(listOf(entity("c-1"), entity("c-2")))
        remote.fetchByIdsResult = listOf(dto("c-1"))

        val result = repository.refreshByIds(setOf(CatalogId("c-1"), CatalogId("c-2")))

        assertTrue(result.isSuccess)
        assertEquals(listOf("c-2"), local.deletedIds)
        assertNotNull(local.findById("c-1"))
    }

    @Test
    fun `given an archived catalog by id then the row stays in the database and stops being shown`() = runTest {
        // Удаление строки каталога каскадит по FK через квесты, секции, темы и уроки до вопросов:
        // архивация уносила бы у игрока весь скачанный курс вместе с прогрессом.
        local.seed(listOf(entity("c-1")))
        remote.fetchByIdsResult = listOf(dto("c-1", archived = true))

        repository.refreshByIds(setOf(CatalogId("c-1")))

        assertNotNull(local.findById("c-1"))
        assertEquals(emptyList(), local.deletedIds)
        assertEquals(emptyList(), repository.observeAll().first())
    }
}
