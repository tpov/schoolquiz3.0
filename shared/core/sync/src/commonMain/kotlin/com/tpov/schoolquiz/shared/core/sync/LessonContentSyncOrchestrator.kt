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
    /**
     * Куда сообщить, сколько записей журнала урока проход не понял.
     *
     * Прежде это число здесь выбрасывалось `.map { }`, и экран показывал ноль, пока журнал урока
     * был полон записей старой формы: пропуск снова не оставлял следа (AD-11).
     */
    private val status: SyncStatusRepository? = null,
    /** Строгость разбора — общая на все четыре журнала (AD-11). */
    private val policy: JournalReadPolicy = JournalReadPolicy.TOLERANT,
    /** Same application gate as catalog reads and full resync; local lesson startup never waits on it. */
    private val gate: SyncGate = SyncGate(),
) {
    suspend fun syncLessonContent(lessonId: LessonId): Result<Unit> =
        gate.withPass {
            try {
                // A runner needs the changed questions for this lesson, not a catalog-wide
                // structure pass. Every question change is also written to this lesson's journal
                // (functions/sync-changes.js); reading the catalog journal here made a cold open
                // wait behind unrelated changes and other sync work.
                syncQuestions(lessonId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun syncQuestContent(questId: QuestId): Result<Unit> =
        gate.withPass {
            try {
                val quest = questRepo.getById(questId) ?: return@withPass Result.success(Unit)
                catalogSync.syncCatalogStructure(quest.catalogId).onFailure { return@withPass Result.failure(it) }

                val lessonIds = collectLessonIds(questId)
                for (lessonId in lessonIds) {
                    syncQuestions(lessonId).onFailure { return@withPass Result.failure(it) }
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun collectLessonIds(questId: QuestId): List<LessonId> {
        val sections = sectionRepo.observeByQuest(questId).first()
        val themes = sections.flatMap { section -> themeRepo.observeBySection(section.id).first() }
        return themes
            .flatMap { theme -> lessonRepo.observeByTheme(theme.id).first() }
            .map { it.id }
            .distinctBy { it.value }
    }

    private suspend fun syncQuestions(lessonId: LessonId): Result<Unit> =
        syncStateRepo
            .syncJournal(
                cursorId = lessonContentSyncCursorId(lessonId),
                policy = policy,
                // В журнале урока лежат только вопросы; запись другого типа или без id читатель не
                // понял, и она считается, а не пропадает (AD-11).
                isReadable = { it.type == Question && it.nodeId.isNotBlank() },
                fetch = { syncChangeRemote.fetchPage(lessonId, it) },
                apply = { changes ->
                    questionRepo.refreshByIds(changes.map { QuestionId(it.nodeId) }.toSet())
                },
            )
            // Число не выбрасывается: оно единственный след пропуска (AD-11). Докладывается и при
            // неудаче применения — непрочитанное от неё прочитанным не становится.
            .onSuccess { status?.recordUnreadableChanges(SyncJournals.LESSON_CONTENT, it) }
            .map { }
}
