package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.sync.CatalogSyncNodeType.Question
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * On-demand sync for lesson payload. The catalog sync keeps the hierarchy fresh;
 * this scope pulls the actual questions only when a lesson is opened or downloaded.
 */
class LessonContentSyncOrchestrator(
    private val catalogSync: CatalogSyncListOrchestrator,
    private val lessonRepo: LessonRepository,
    private val themeRepo: ThemeRepository,
    private val sectionRepo: SectionRepository,
    private val questRepo: QuestRepository,
    private val questionRepo: QuestionRepository,
    private val syncStateRepo: SyncStateRepository,
    private val syncChangeRemote: LessonContentSyncChangeRemoteDataSource,
) {

    suspend fun syncLessonContent(lessonId: LessonId): Result<Unit> =
        try {
            refreshContainingStructure(lessonId).onFailure { return Result.failure(it) }
            syncQuestions(lessonId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun syncQuestContent(questId: QuestId): Result<Unit> =
        try {
            val quest = questRepo.getById(questId) ?: return Result.success(Unit)
            catalogSync.syncCatalogStructure(quest.catalogId).onFailure { return Result.failure(it) }

            val lessonIds = collectLessonIds(questId)
            for (lessonId in lessonIds) {
                syncQuestions(lessonId).onFailure { return Result.failure(it) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun refreshContainingStructure(lessonId: LessonId): Result<Unit> {
        val lesson = lessonRepo.getById(lessonId) ?: return Result.success(Unit)
        val theme = themeRepo.getById(lesson.themeId) ?: return Result.success(Unit)
        val section = sectionRepo.getById(theme.sectionId) ?: return Result.success(Unit)
        val quest = questRepo.getById(section.questId) ?: return Result.success(Unit)
        return catalogSync.syncCatalogStructure(quest.catalogId)
    }

    private suspend fun collectLessonIds(questId: QuestId): List<LessonId> {
        val sections = sectionRepo.observeByQuest(questId).first()
        val themes = sections.flatMap { section -> themeRepo.observeBySection(section.id).first() }
        return themes
            .flatMap { theme -> lessonRepo.observeByTheme(theme.id).first() }
            .map { it.id }
            .distinctBy { it.value }
    }

    private suspend fun syncQuestions(lessonId: LessonId): Result<Unit> {
        val cursorId = lessonContentSyncCursorId(lessonId)
        val cursor = syncStateRepo.getCursor(cursorId)
        val changes = syncChangeRemote.fetchChangedSince(lessonId, cursor)
            .filter { it.nodeId.isNotBlank() && it.type == Question }
        if (changes.isEmpty()) return Result.success(Unit)

        val questionIds = changes.map { QuestionId(it.nodeId) }.toSet()
        questionRepo.refreshByIds(questionIds).onFailure { return Result.failure(it) }
        syncStateRepo.setCursor(cursorId, changes.maxOf { it.changedAtMs })
        return Result.success(Unit)
    }
}
