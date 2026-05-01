package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Sync-list based catalog content sync.
 *
 * Server contract:
 * `catalogs/{catalogId}/sync_changes/{changeId}` contains tiny records:
 * `type`, `id`, and cursor metadata `changedAtMs`.
 *
 * The change record only tells the client which flat document to fetch. The
 * actual node is read from its own collection by id.
 */
class CatalogSyncListOrchestrator(
    private val catalogRepo: CatalogRepository,
    private val questRepo: QuestRepository,
    private val sectionRepo: SectionRepository,
    private val themeRepo: ThemeRepository,
    private val lessonRepo: LessonRepository,
    private val questionRepo: QuestionRepository,
    private val syncStateRepo: SyncStateRepository,
    private val syncChangeRemote: CatalogSyncChangeRemoteDataSource,
) : Syncable {

    override suspend fun sync(): Result<Unit> {
        return try {
            catalogRepo.refreshFromRemote().onFailure { return Result.failure(it) }

            val catalogs = catalogRepo.observeAll().first()
            catalogs
                .maxOfOrNull { it.lastModifiedAt }
                ?.let { syncStateRepo.setCursor(CATALOG_LIST_CURSOR_ID, it) }

            val catalogIds = catalogs
                .map { it.id }
                .plus(CatalogId(ON_DEMAND_COURSES_CATALOG_ID))
                .distinctBy { it.value }
            for (catalogId in catalogIds) {
                syncCatalog(
                    catalogId,
                    includeQuestions = catalogId.value != ON_DEMAND_COURSES_CATALOG_ID,
                ).onFailure { return Result.failure(it) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCatalogStructure(catalogId: CatalogId): Result<Unit> =
        syncCatalog(catalogId, includeQuestions = false)

    private suspend fun syncCatalog(
        catalogId: CatalogId,
        includeQuestions: Boolean,
    ): Result<Unit> {
        catalogRepo.refreshByIds(setOf(catalogId)).onFailure { return Result.failure(it) }
        val cursorId = catalogSyncCursorId(catalogId)
        val cursor =
            if (shouldForceFullCourseArchiveSync(catalogId)) {
                0L
            } else {
                syncStateRepo.getCursor(cursorId)
            }
        val changes = syncChangeRemote.fetchChangedSince(catalogId, cursor)
            .filter { it.nodeId.isNotBlank() }
        if (changes.isEmpty()) return Result.success(Unit)

        applyChanges(changes, includeQuestions).onFailure { return Result.failure(it) }
        syncStateRepo.setCursor(cursorId, changes.maxOf { it.changedAtMs })
        return Result.success(Unit)
    }

    private suspend fun shouldForceFullCourseArchiveSync(catalogId: CatalogId): Boolean =
        catalogId.value == ON_DEMAND_COURSES_CATALOG_ID &&
            questRepo.observeByCatalog(catalogId, ARCHIVE_SHELF).first().isEmpty()

    private suspend fun applyChanges(
        changes: List<CatalogSyncChange>,
        includeQuestions: Boolean,
    ): Result<Unit> {
        val grouped = changes
            .groupBy { it.type }
            .mapValues { (_, items) -> items.map { it.nodeId }.toSet() }

        grouped[CatalogSyncNodeType.Catalog]
            ?.map(::CatalogId)
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?.let { catalogRepo.refreshByIds(it).onFailure { e -> return Result.failure(e) } }

        grouped[CatalogSyncNodeType.Quest]
            ?.map(::QuestId)
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?.let { questRepo.refreshByIds(it).onFailure { e -> return Result.failure(e) } }

        grouped[CatalogSyncNodeType.Section]
            ?.map(::SectionId)
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?.let { sectionRepo.refreshByIds(it).onFailure { e -> return Result.failure(e) } }

        grouped[CatalogSyncNodeType.Theme]
            ?.map(::ThemeId)
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?.let { themeRepo.refreshByIds(it).onFailure { e -> return Result.failure(e) } }

        grouped[CatalogSyncNodeType.Lesson]
            ?.map(::LessonId)
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?.let { lessonRepo.refreshByIds(it).onFailure { e -> return Result.failure(e) } }

        if (includeQuestions) {
            grouped[CatalogSyncNodeType.Question]
                ?.map(::QuestionId)
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?.let { questionRepo.refreshByIds(it).onFailure { e -> return Result.failure(e) } }
        }

        return Result.success(Unit)
    }
}

private const val ON_DEMAND_COURSES_CATALOG_ID = "courses"
private const val ARCHIVE_SHELF = "archive"
