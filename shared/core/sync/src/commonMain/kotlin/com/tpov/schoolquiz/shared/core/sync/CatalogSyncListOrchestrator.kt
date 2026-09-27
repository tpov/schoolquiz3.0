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
    /**
     * Ворота, общие с полным ресинком.
     *
     * По умолчанию свои — так тесты и другие вызывающие остаются простыми. В приложении сюда
     * передаётся единственный экземпляр: два означали бы, что сброс курсоров и обычный проход
     * снова могут пересечься.
     */
    private val gate: SyncGate = SyncGate(),
    /**
     * Куда сообщить, сколько записей журнала проход не понял.
     *
     * Необязателен: тесты и другие вызывающие остаются простыми. В приложении он есть — иначе
     * пропуск не оставляет следа, и «backfill закончен» не следует ни из чего (AD-11).
     */
    private val status: SyncStatusRepository? = null,
    /**
     * Строгость разбора журнала — общая на все четыре (AD-11). До backfill старые формы терпятся
     * и считаются, после — останавливают проход, и курсор через них не проходит.
     */
    private val policy: JournalReadPolicy = JournalReadPolicy.TOLERANT,
) : Syncable {
    override suspend fun sync(): Result<Unit> = gate.withPass { syncAll() }

    private suspend fun syncAll(): Result<Unit> {
        return try {
            catalogRepo.refreshFromRemote().onFailure { return Result.failure(it) }

            val catalogs = catalogRepo.observeAll().first()
            // Курсор списка — такая же пара `(время, id)`, как у четырёх журналов (AD-31). Прежде
            // сюда уходило одно время с пустым id, и следующий проход просил «строго больше»: курс,
            // ставший видимым в ту же миллисекунду, терялся навсегда.
            catalogs
                .maxOfOrNull { SyncCursor(it.lastModifiedAt, it.id.value) }
                ?.let { syncStateRepo.setCursor(CATALOG_LIST_CURSOR_ID, it) }

            val catalogIds =
                catalogs
                    .map { it.id }
                    .plus(CatalogId(ON_DEMAND_COURSES_CATALOG_ID))
                    .distinctBy { it.value }
            // Один каталог, который не читается, не отменяет остальные. Прежде выход по первой
            // же неудаче означал, что каталог, стоящий в списке после сломанного, не
            // синхронизируется никогда — а порядок здесь произвольный, по времени изменения.
            // Наружу отдаётся первая неудача, но уже после того, как все попробовали.
            var firstFailure: Throwable? = null
            var unreadable = 0
            for (catalogId in catalogIds) {
                syncCatalog(
                    catalogId,
                    includeQuestions = catalogId.value != ON_DEMAND_COURSES_CATALOG_ID,
                )
                    .onSuccess { unreadable += it }
                    .onFailure { if (firstFailure == null) firstFailure = it }
            }
            // Число описывает проход целиком, включая проход, который где-то упал: непрочитанное
            // от этого не становится прочитанным.
            status?.recordUnreadableChanges(SyncJournals.CATALOGS, unreadable)
            firstFailure?.let { Result.failure(it) } ?: Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCatalogStructure(catalogId: CatalogId): Result<Unit> =
        gate.withPass { syncCatalog(catalogId, includeQuestions = false).map { } }

    /** @return сколько записей журнала этого каталога прочитать не удалось. */
    private suspend fun syncCatalog(
        catalogId: CatalogId,
        includeQuestions: Boolean,
    ): Result<Int> {
        catalogRepo.refreshByIds(setOf(catalogId)).onFailure { return Result.failure(it) }
        // Журнал читается страницами от сохранённого курсора-пары (AD-31). Форса «с нуля» здесь
        // нет: он существует только как явная операция ресинка (AD-30).
        return syncStateRepo.syncJournal(
            cursorId = catalogSyncCursorId(catalogId),
            policy = policy,
            // Запись с пустым `id` столь же бесполезна, как и нераспознанная, и по той же
            // причине: узел, который надо перечитать, не назван. Раньше она отсеивалась молча.
            isReadable = { it.nodeId.isNotBlank() },
            fetch = { syncChangeRemote.fetchPage(catalogId, it) },
            apply = { applyChanges(it, includeQuestions) },
        )
    }

    private suspend fun applyChanges(
        changes: List<CatalogSyncChange>,
        includeQuestions: Boolean,
    ): Result<Unit> {
        val grouped =
            changes
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
