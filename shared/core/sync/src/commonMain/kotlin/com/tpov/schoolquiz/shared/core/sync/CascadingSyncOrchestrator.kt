package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock

/**
 * Central orchestrator for 6-level cascading sync.
 *
 * Invariants (ADR-CMP-49):
 *  - [syncCascade] is `internal`; [sync] is the single public entry point via [Syncable].
 *  - Cursor: [SyncStateRepository.getCursor] at the start of each level;
 *    [SyncStateRepository.setCursor] ONLY after the ENTIRE subtree for that level succeeds
 *    (subtree-atomic advance). All 6 levels use Clock.System.now() (freshTime) — uniform
 *    strategy (ADR Amendment "Cursor Advance Strategy" in 03-decisions.md).
 *  - Cursor value: Clock.System.now() sampled once per [syncCascade] entry (freshTime).
 *    Conservative strategy — items modified between freshTime and fetch-completion are
 *    re-fetched on next sync but never missed (ADR Amendment in 03-decisions.md).
 *  - Batch: parentIds > 30 are split into chunks of ≤30 before each repository call.
 *  - [availableShelves] is hard-coded to {"home","arena"} for MVP (future: userStatsRepo).
 *  - [authRepo.currentUid] == null → guest mode: Quest Query A is skipped by [QuestRepository].
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §4
 */
class CascadingSyncOrchestrator(
    private val catalogRepo: CatalogRepository,
    private val questRepo: QuestRepository,
    private val sectionRepo: SectionRepository,
    private val themeRepo: ThemeRepository,
    private val lessonRepo: LessonRepository,
    private val questionRepo: QuestionRepository,
    private val syncStateRepo: SyncStateRepository,
    private val authRepo: AuthRepository,
    @Suppress("unused") private val userStatsRepo: UserStatsRepository,
) : Syncable {

    companion object {
        private val availableShelves = setOf("home", "arena")
        private const val BATCH_SIZE = 30
    }

    override suspend fun sync(): Result<Unit> = syncCascade(SyncLevel.Catalog, emptySet())

    /**
     * Runs the cascading sync from [level] downward, using [parentIds] as parent-id filter.
     *
     * Called as `sync() = syncCascade(SyncLevel.Catalog, emptySet())` for a full sync.
     * Internal visibility allows direct level-by-level testing.
     */
    internal suspend fun syncCascade(level: SyncLevel, parentIds: Set<String>): Result<Unit> {
        return try {
            // UID is captured once at cascade start. Mid-cascade sign-out relies on
            // Firebase server-side validation (stale token → Result.failure on next fetchOwnChanged).
            // No re-read per step — acceptable trade-off for MVP. See security review phase-03.
            val uid = authRepo.currentUid()
            val freshTime = Clock.System.now().toEpochMilliseconds()
            cascadeLevel(level, parentIds, uid, freshTime)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Expression form: compile ERROR if a new SyncLevel value is added without a handler.
    private suspend fun cascadeLevel(
        level: SyncLevel,
        parentIds: Set<String>,
        uid: String?,
        freshTime: Long,
    ): Result<Unit> = when (level) {
        SyncLevel.Catalog  -> cascadeCatalog(uid, freshTime)
        SyncLevel.Quest    -> cascadeQuest(parentIds, uid, freshTime)
        SyncLevel.Section  -> cascadeSection(parentIds, uid, freshTime)
        SyncLevel.Theme    -> cascadeTheme(parentIds, uid, freshTime)
        SyncLevel.Lesson   -> cascadeLesson(parentIds, uid, freshTime)
        SyncLevel.Question -> cascadeQuestion(parentIds, freshTime)
    }

    private suspend fun cascadeCatalog(uid: String?, freshTime: Long): Result<Unit> {
        val changedCatalogIds = catalogRepo.refreshFromRemote()
            .fold(
                onSuccess = { ids -> ids.map { it.value }.toSet() },
                onFailure = { return Result.failure(it) },
            )
        if (changedCatalogIds.isEmpty()) {
            syncStateRepo.setCursor(SyncLevel.Catalog.collectionId, freshTime)
            return Result.success(Unit)
        }
        return cascadeLevel(SyncLevel.Quest, changedCatalogIds, uid, freshTime)
            .also { if (it.isSuccess) syncStateRepo.setCursor(SyncLevel.Catalog.collectionId, freshTime) }
    }

    private suspend fun cascadeQuest(parentIds: Set<String>, uid: String?, freshTime: Long): Result<Unit> {
        if (parentIds.isEmpty()) return Result.success(Unit)
        val cursor = syncStateRepo.getCursor(SyncLevel.Quest.collectionId)
        val changedQuestIds = mutableSetOf<String>()
        for (batch in parentIds.toList().chunked(BATCH_SIZE)) {
            questRepo.refreshFromRemote(
                currentUserUid = uid,
                availableShelves = availableShelves,
                catalogIdsToSync = batch.map { CatalogId(it) }.toSet(),
                cursor = cursor,
            ).fold(
                onSuccess = { ids -> changedQuestIds.addAll(ids.map { it.value }) },
                onFailure = { return Result.failure(it) },
            )
        }
        if (changedQuestIds.isEmpty()) {
            syncStateRepo.setCursor(SyncLevel.Quest.collectionId, freshTime)
            return Result.success(Unit)
        }
        return cascadeLevel(SyncLevel.Section, changedQuestIds, uid, freshTime)
            .also { if (it.isSuccess) syncStateRepo.setCursor(SyncLevel.Quest.collectionId, freshTime) }
    }

    private suspend fun cascadeSection(parentIds: Set<String>, uid: String?, freshTime: Long): Result<Unit> {
        if (parentIds.isEmpty()) return Result.success(Unit)
        val cursor = syncStateRepo.getCursor(SyncLevel.Section.collectionId)
        val changedSectionIds = mutableSetOf<String>()
        for (batch in parentIds.toList().chunked(BATCH_SIZE)) {
            sectionRepo.refreshByParents(
                questIds = batch.map { QuestId(it) }.toSet(),
                cursor = cursor,
            ).fold(
                onSuccess = { ids -> changedSectionIds.addAll(ids.map { it.value }) },
                onFailure = { return Result.failure(it) },
            )
        }
        if (changedSectionIds.isEmpty()) {
            syncStateRepo.setCursor(SyncLevel.Section.collectionId, freshTime)
            return Result.success(Unit)
        }
        return cascadeLevel(SyncLevel.Theme, changedSectionIds, uid, freshTime)
            .also { if (it.isSuccess) syncStateRepo.setCursor(SyncLevel.Section.collectionId, freshTime) }
    }

    private suspend fun cascadeTheme(parentIds: Set<String>, uid: String?, freshTime: Long): Result<Unit> {
        if (parentIds.isEmpty()) return Result.success(Unit)
        val cursor = syncStateRepo.getCursor(SyncLevel.Theme.collectionId)
        val changedThemeIds = mutableSetOf<String>()
        for (batch in parentIds.toList().chunked(BATCH_SIZE)) {
            themeRepo.refreshByParents(
                sectionIds = batch.map { SectionId(it) }.toSet(),
                cursor = cursor,
            ).fold(
                onSuccess = { ids -> changedThemeIds.addAll(ids.map { it.value }) },
                onFailure = { return Result.failure(it) },
            )
        }
        if (changedThemeIds.isEmpty()) {
            syncStateRepo.setCursor(SyncLevel.Theme.collectionId, freshTime)
            return Result.success(Unit)
        }
        return cascadeLevel(SyncLevel.Lesson, changedThemeIds, uid, freshTime)
            .also { if (it.isSuccess) syncStateRepo.setCursor(SyncLevel.Theme.collectionId, freshTime) }
    }

    private suspend fun cascadeLesson(parentIds: Set<String>, uid: String?, freshTime: Long): Result<Unit> {
        if (parentIds.isEmpty()) return Result.success(Unit)
        val cursor = syncStateRepo.getCursor(SyncLevel.Lesson.collectionId)
        val changedLessonIds = mutableSetOf<String>()
        for (batch in parentIds.toList().chunked(BATCH_SIZE)) {
            lessonRepo.refreshByParents(
                themeIds = batch.map { ThemeId(it) }.toSet(),
                cursor = cursor,
            ).fold(
                onSuccess = { ids -> changedLessonIds.addAll(ids.map { it.value }) },
                onFailure = { return Result.failure(it) },
            )
        }
        if (changedLessonIds.isEmpty()) {
            syncStateRepo.setCursor(SyncLevel.Lesson.collectionId, freshTime)
            return Result.success(Unit)
        }
        return cascadeLevel(SyncLevel.Question, changedLessonIds, uid, freshTime)
            .also { if (it.isSuccess) syncStateRepo.setCursor(SyncLevel.Lesson.collectionId, freshTime) }
    }

    private suspend fun cascadeQuestion(parentIds: Set<String>, freshTime: Long): Result<Unit> {
        if (parentIds.isEmpty()) return Result.success(Unit)
        val cursor = syncStateRepo.getCursor(SyncLevel.Question.collectionId)
        for (batch in parentIds.toList().chunked(BATCH_SIZE)) {
            questionRepo.refreshByParents(
                lessonIds = batch.map { LessonId(it) }.toSet(),
                cursor = cursor,
            ).onFailure { return Result.failure(it) }
        }
        // Question is a leaf node — cursor advance after all batches succeed is correct.
        syncStateRepo.setCursor(SyncLevel.Question.collectionId, freshTime)
        return Result.success(Unit)
    }
}
