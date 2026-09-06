package com.tpov.schoolquiz.shared.core.catalog.data

import com.tpov.schoolquiz.shared.core.catalog.data.mapper.toDomain
import com.tpov.schoolquiz.shared.core.catalog.data.mapper.toEntity
import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.core.sync.CATALOG_LIST_CURSOR_ID
import com.tpov.schoolquiz.shared.core.sync.MAX_PAGES_PER_RUN
import com.tpov.schoolquiz.shared.core.sync.SYNC_PAGE_SIZE
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class CatalogRepositoryImpl(
    private val local: CatalogLocalDataSource,
    private val remote: CatalogRemoteDataSource,
    private val storageUrlResolver: StorageUrlResolver,
    private val syncStateRepo: SyncStateRepository,
) : CatalogRepository {
    override fun observeAll(): Flow<List<Catalog>> =
        local.observeAll().map { list ->
            list.map { it.toDomain() }.sortedBy { it.id.value }
        }

    /**
     * Список каталогов страницами, от курсора-пары (AD-31).
     *
     * Курсор берётся целиком, а не одной половиной: раньше отсюда уходило только `changedAtMs`, и
     * каталог, изменённый в ту же миллисекунду, на которой уже стоял курсор, не попадал в список
     * никогда — а свой журнал `catalogs/{id}/sync_changes` у него не читается, пока каталога в
     * списке нет. Новый курс не появлялся у игрока, пока не изменится какой-нибудь другой.
     *
     * Страницы читаются до конца этим же проходом, но не бесконечно: коллекция пополняется во
     * время чтения. Курсор здесь не сохраняется — его двигает читающий оркестратор по тому, что
     * реально легло в базу.
     */
    override suspend fun refreshFromRemote(): Result<Set<CatalogId>> {
        return try {
            val saved = syncStateRepo.getCursor(CATALOG_LIST_CURSOR_ID)
            var at = if (local.observeAll().first().isEmpty()) SyncCursor.BEGINNING else saved
            val changedIds = mutableSetOf<CatalogId>()
            var pages = 0
            while (pages < MAX_PAGES_PER_RUN) {
                val dtos = remote.fetchChangedSince(at, SYNC_PAGE_SIZE)
                if (dtos.isEmpty()) break
                for (dto in dtos) {
                    if (applyDto(dto)) {
                        changedIds.add(CatalogId(dto.id))
                    }
                }
                // По максимуму, а не по последнему: порядок гарантирует запрос, но фейк в тесте —
                // нет, и курсор, вставший ниже уже прочитанного, отдал бы те же строки снова.
                at = dtos.maxOf { SyncCursor(it.lastModifiedAt, it.id) }
                pages++
                if (dtos.size < SYNC_PAGE_SIZE) break
            }
            Result.success(changedIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshByIds(ids: Set<CatalogId>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        return try {
            val requested = ids.map { it.value }.toSet()
            val dtos = remote.fetchByIds(requested)
            val returned = dtos.map { it.id }.toSet()
            for (missingId in requested - returned) {
                local.deleteById(missingId)
            }
            for (dto in dtos) {
                applyDto(dto)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getById(id: CatalogId): Catalog? = local.findById(id.value)?.toDomain()

    private suspend fun applyDto(dto: CatalogDto): Boolean {
        val baseEntity = dto.toEntity()
        val existing = local.findById(dto.id)
        val path = baseEntity.picturePath
        val retainedUrl =
            existing?.pictureUrl?.takeIf {
                existing.picturePath == path
            }
        val resolvedUrl =
            when {
                path == null -> null
                retainedUrl != null -> retainedUrl
                !path.startsWith("catalog-pictures/") -> null
                else ->
                    runCatching { storageUrlResolver(path) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
            }
        val entity = baseEntity.copy(pictureUrl = resolvedUrl)
        // `archived` СКРЫВАЕТ каталог, а не удаляет его (AD-26, AC 7.6): строка и всё скачанное под
        // ней остаются, из списка каталог уходит фильтром самого запроса (`WHERE archived = 0`).
        // Прежде здесь было `deleteById`, а удаление строки каталога каскадит по FK через квесты,
        // секции, темы и уроки до вопросов — архивация уносила у игрока весь скачанный курс.
        local.upsertByIdIfNewerVersion(entity)
        // Изменившимся считается только видимый: спускаться в детей скрытого каталога незачем.
        return !dto.archived
    }
}
