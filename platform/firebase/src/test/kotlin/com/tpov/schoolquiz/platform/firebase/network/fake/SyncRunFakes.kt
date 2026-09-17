package com.tpov.schoolquiz.platform.firebase.network.fake

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentEntity
import com.tpov.schoolquiz.shared.core.persistence.SectionEntity
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChange
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncNodeType
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.core.sync.SyncRunKind
import com.tpov.schoolquiz.shared.core.sync.SyncRunTrace
import com.tpov.schoolquiz.shared.core.sync.SyncRunTraceStore
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.section.data.SectionLocalDataSource
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// Обстановка вокруг проверяемого шага.
//
// Всё, что здесь есть, стоит СНАРУЖИ проверяемой цепочки: проверяется путь от исключения SDK до
// снимка состояния, и на этом пути ни один из этих классов не лежит. Соседние по прогону порты
// молчат намеренно — иначе непонятно, чью именно неудачу назвал снимок.

/**
 * След прогона в памяти — той же формы, что строка в базе: владелец и вид прогона.
 *
 * Больше, чем нужно снимку, он не умеет: тестам важно, что ветвь доехала, а не как она хранится.
 */
class MemorySyncRunTraceStore : SyncRunTraceStore {
    private val rows = MutableStateFlow<Map<Pair<String, SyncRunKind>, SyncRunTrace>>(emptyMap())

    override fun observeTraces(ownerUid: String): Flow<Map<SyncRunKind, SyncRunTrace>> =
        rows.map { all ->
            all.filterKeys { it.first == ownerUid }.mapKeys { (key, _) -> key.second }
        }

    override suspend fun recordSuccess(
        ownerUid: String,
        kind: SyncRunKind,
        atMs: Long,
    ) {
        rows.value = rows.value + ((ownerUid to kind) to SyncRunTrace(lastSuccessAtMs = atMs, lastError = null))
    }

    override suspend fun recordFailure(
        ownerUid: String,
        kind: SyncRunKind,
        error: SyncError,
    ) {
        val previous = rows.value[ownerUid to kind] ?: SyncRunTrace()
        rows.value = rows.value + ((ownerUid to kind) to previous.copy(lastError = error))
    }
}

/** Пустая очередь: снимку она нужна только чтобы собраться, к исходу прогона отношения не имеет. */
class EmptyOutbox : OutboxStore {
    override suspend fun enqueue(record: OutboxRecord): OutboxRecord = record

    override suspend fun dueRecords(
        ownerUid: String,
        nowMs: Long,
        limit: Int,
    ): List<OutboxRecord> = emptyList()

    override suspend fun apply(
        id: Long,
        decision: OutboxDecision,
    ) = Unit

    override suspend fun remove(id: Long) = Unit

    override fun observeCounts(ownerUid: String): Flow<OutboxCounts> = flowOf(OutboxCounts())

    override fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ): Flow<OutboxEntitySync> = flowOf(OutboxEntitySync.None)

    override suspend fun findByEntityRef(
        ownerUid: String,
        entityRef: String,
    ): OutboxRecord? = null

    override suspend fun quarantined(ownerUid: String): List<OutboxRecord> = emptyList()

    override suspend fun countPending(ownerUid: String): Int = 0

    override suspend fun forget(ownerUid: String) = Unit
}

/** Один каталог, который читается без приключений: неудача должна прийти с шага ниже. */
class OneCatalogRepository(private val catalogId: CatalogId) : CatalogRepository {
    private val catalog =
        Catalog(
            id = catalogId,
            name = "каталог",
            picturePath = null,
            pictureUrl = null,
            version = 1L,
            lastModifiedAt = 1_000L,
            archived = false,
        )

    override fun observeAll(): Flow<List<Catalog>> = flowOf(listOf(catalog))

    override suspend fun refreshFromRemote(): Result<Set<CatalogId>> = Result.success(setOf(catalogId))

    override suspend fun refreshByIds(ids: Set<CatalogId>): Result<Unit> = Result.success(Unit)

    override suspend fun getById(id: CatalogId): Catalog? = catalog.takeIf { it.id == id }
}

/**
 * Журнал каталога, в котором ровно одна запись: «перечитай этот раздел».
 *
 * Ради неё журнал и читается — и именно на перечитывании узла связь обрывается.
 */
class OneSectionChangeJournal(
    private val catalogId: CatalogId,
    private val sectionId: String,
) : CatalogSyncChangeRemoteDataSource {
    override suspend fun fetchPage(
        catalogId: CatalogId,
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<CatalogSyncChange> =
        if (catalogId != this.catalogId || cursor != SyncCursor.BEGINNING) {
            SyncChangePage(changes = emptyList(), nextCursor = null, hasMore = false)
        } else {
            SyncChangePage(
                changes =
                    listOf(
                        CatalogSyncChange(
                            catalogId = catalogId,
                            type = CatalogSyncNodeType.Section,
                            nodeId = sectionId,
                            changedAtMs = 2_000L,
                        ),
                    ),
                nextCursor = SyncCursor(changedAtMs = 2_000L, docId = sectionId),
                hasMore = false,
            )
        }
}

/** Локальная сторона раздела: до неё проход не доходит — связь рвётся на чтении. */
class UnusedSectionLocal : SectionLocalDataSource {
    override fun observeByQuest(questId: String): Flow<List<SectionEntity>> = emptyFlow()

    override suspend fun upsertByIdIfNewerVersion(entity: SectionEntity) = Unit

    override suspend fun deleteById(id: String) = Unit

    override suspend fun findById(id: String): SectionEntity? = null
}

/** Соседние по каталогу порты, которых эта запись журнала не касается. */
class SilentQuestRepository : QuestRepository {
    override fun observeMyQuests(
        authorUid: String,
        catalogId: CatalogId?,
    ): Flow<List<Quest>> = emptyFlow()

    override fun observeByCatalog(
        catalogId: CatalogId,
        shelf: String,
    ): Flow<List<Quest>> = emptyFlow()

    override fun observeByShelf(shelf: String): Flow<List<Quest>> = emptyFlow()

    override suspend fun getById(id: QuestId): Quest? = null

    override suspend fun refreshFromRemote(
        currentUserUid: String?,
        availableShelves: Set<String>,
        catalogIdsToSync: Set<CatalogId>,
        cursor: Long,
    ): Result<Set<QuestId>> = Result.success(emptySet())
}

class SilentThemeRepository : ThemeRepository {
    override fun observeBySection(sectionId: SectionId): Flow<List<Theme>> = emptyFlow()

    override suspend fun getById(id: ThemeId): Theme? = null

    override suspend fun refreshByParents(
        sectionIds: Set<SectionId>,
        cursor: Long,
    ): Result<Set<ThemeId>> = Result.success(emptySet())
}

class SilentLessonRepository : LessonRepository {
    override fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>> = emptyFlow()

    override suspend fun getById(id: LessonId): Lesson? = null

    override suspend fun refreshByParents(
        themeIds: Set<ThemeId>,
        cursor: Long,
    ): Result<Set<LessonId>> = Result.success(emptySet())
}

class SilentQuestionRepository : QuestionRepository {
    override fun observeByLesson(lessonId: LessonId): Flow<List<Question>> = emptyFlow()

    override suspend fun getById(id: QuestionId): Question? = null

    override suspend fun refreshByParents(
        lessonIds: Set<LessonId>,
        cursor: Long,
    ): Result<Unit> = Result.success(Unit)
}

/** Локальная сторона авторских черновиков: журнал обрывается раньше, чем до неё дойдёт очередь. */
@Suppress("TooManyFunctions")
class UnusedQuestAuthoringLocal : QuestAuthoringLocalDataSource {
    override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>> = emptyFlow()

    override fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?> = emptyFlow()

    override suspend fun getDraft(draftId: String): QuestAuthoringEntityBundle? = null

    override suspend fun activateDraft(
        ownerUid: String,
        draftId: String,
    ) = Unit

    override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringEntityBundle? = null

    override suspend fun saveDraft(bundle: QuestAuthoringEntityBundle) = Unit

    override suspend fun upsertQuestion(question: DraftQuestionEntity) = Unit

    override suspend fun queueArenaSubmission(
        outboxRow: OutboxEntity,
        draftId: String,
        requestedAtMs: Long,
    ) = Unit

    override suspend fun findOwnerUidsAwaitingReview(): Set<String> = emptySet()

    override suspend fun applyPublication(
        draftId: String,
        updatedAtMs: Long,
    ) = Unit

    override suspend fun applyRejection(
        draftId: String,
        reason: String?,
        updatedAtMs: Long,
    ) = Unit

    override suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot) = Unit

    override suspend fun setDraftStatus(
        draftId: String,
        status: String,
        updatedAtMs: Long,
    ) = Unit

    override suspend fun resolveArenaConflict(
        conflictedRowId: Long,
        replacement: OutboxEntity,
        draftId: String,
        updatedAtMs: Long,
    ) = Unit
}

/** Локальная сторона заданий на ревью: до неё проход тоже не доходит. */
class UnusedReviewAssignmentLocal : ReviewAssignmentLocalDataSource {
    override fun observeAssignments(ownerUid: String): Flow<List<ReviewAssignmentEntity>> = emptyFlow()

    override suspend fun findAssignments(ownerUid: String): List<ReviewAssignmentEntity> = emptyList()

    override suspend fun replaceAssignments(
        ownerUid: String,
        assignments: List<ReviewAssignmentEntity>,
    ) = Unit

    override suspend fun applyAssignmentChanges(
        ownerUid: String,
        changedIds: Set<String>,
        assignments: List<ReviewAssignmentEntity>,
    ) = Unit
}

/** Строка собственных чисел аккаунта: её пишут после чтения, а чтение не удаётся. */
class UnusedUserStatsDao : UserStatsDao {
    override fun observeByUid(uid: String): Flow<UserStatsEntity?> = flowOf(null)

    override suspend fun findByUid(uid: String): UserStatsEntity? = null

    override suspend fun upsert(entity: UserStatsEntity) = Unit

    override suspend fun updateDeveloperLevel(
        uid: String,
        value: Int,
    ) = Unit

    // Половина очереди этому фейку не нужна: он про профиль, а не про отправку.
    override suspend fun enqueueOutboxRow(entity: OutboxEntity): Long = error("очередь этому фейку не нужна")

    override suspend fun outboxRowState(mutationId: String): String? = null

    override suspend fun deleteOutboxRow(mutationId: String): Unit = error("очередь этому фейку не нужна")

    override fun observeQuarantinedEntityRefs(
        ownerUid: String,
        operation: String,
    ): Flow<List<String>> = flowOf(emptyList())

    override suspend fun undeliveredPayloads(
        ownerUid: String,
        operation: String,
    ): List<String> = emptyList()
}
