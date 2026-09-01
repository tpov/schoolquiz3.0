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
            // Один каталог, который не читается, не отменяет остальные. Прежде выход по первой
            // же неудаче означал, что каталог, стоящий в списке после сломанного, не
            // синхронизируется никогда — а порядок здесь произвольный, по времени изменения.
            // Наружу отдаётся первая неудача, но уже после того, как все попробовали.
            var firstFailure: Throwable? = null
            for (catalogId in catalogIds) {
                syncCatalog(
                    catalogId,
                    includeQuestions = catalogId.value != ON_DEMAND_COURSES_CATALOG_ID,
                ).onFailure { if (firstFailure == null) firstFailure = it }
            }
            firstFailure?.let { Result.failure(it) } ?: Result.success(Unit)
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
        // Журнал читается страницами (AD-31): полная перепубликация большого курса иначе
        // приходит одним ответом, размер которого ограничен только автором.
        var at = SyncCursor(cursor)
        var pages = 0
        while (pages < MAX_PAGES_PER_RUN) {
            val page = syncChangeRemote.fetchPage(catalogId, at)
            val changes = page.changes.filter { it.nodeId.isNotBlank() }
            // Выход по пустой странице, а не по «нет распознанных изменений»: страница, целиком
            // состоящая из битых записей, иначе останавливала бы каталог навсегда — курсор не
            // двигался, и следующий проход читал бы её же.
            if (page.changes.isEmpty()) break

            if (changes.isNotEmpty()) {
                applyChanges(changes, includeQuestions).onFailure { return Result.failure(it) }
            }
            // Курсор двигается только после успешного применения: обрыв на середине страницы
            // означает, что следующий проход перечитает её же.
            page.nextCursor?.let {
                at = it
                syncStateRepo.setCursor(cursorId, it.changedAtMs)
            }
            pages++
            if (!page.hasMore) break
        }
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

/**
 * Deliberately an id, not [QuestType.COURSE].
 *
 * This catalog is synced even when the local catalog list is still empty — that is the whole
 * point, it bootstraps courses on a fresh install. The type lives on the catalog record, which
 * is exactly the data that does not exist yet at that moment, so it cannot drive this decision.
 */
private const val ON_DEMAND_COURSES_CATALOG_ID = "courses"
private const val ARCHIVE_SHELF = "archive"

/**
 * Сколько страниц журнала берём за один проход.
 *
 * Не бесконечно: журнал пополняется во время чтения, и без потолка один проход мог бы не
 * закончиться никогда. Остаток доедет следующим заходом — курсор уже сдвинут.
 */
private const val MAX_PAGES_PER_RUN = 50
