package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.command.CreateQuestDraftCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLesson
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSection
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSectionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftTheme
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftThemeId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraft
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.CancellationException

class CreateQuestDraftUseCase(
    private val repository: QuestAuthoringRepository,
    private val idProvider: QuestAuthoringIdProvider,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) {
    suspend operator fun invoke(command: CreateQuestDraftCommand): Result<QuestDraftId> {
        return try {
            val now = timestampProvider.nowMs()
            val draftId = QuestDraftId(idProvider.nextId("draft"))
            val sectionId = DraftSectionId(idProvider.nextId("draft-section"))
            val themeId = DraftThemeId(idProvider.nextId("draft-theme"))
            val lessonId = DraftLessonId(idProvider.nextId("draft-lesson"))
            val draft = QuestDraft(
                id = draftId,
                ownerUid = command.ownerUid,
                catalogId = command.catalogId,
                title = command.title.trim(),
                description = command.description?.trim()?.takeIf { it.isNotEmpty() },
                defaultLanguage = command.defaultLanguage,
                defaultDifficulty = command.defaultDifficulty,
                status = QuestDraftStatus.SAVED,
                localRevision = 1L,
                serverRevision = null,
                publicQuestId = command.sourceQuestId,
                createdAtMs = now,
                updatedAtMs = now,
                isActive = true,
            )
            val bundle = QuestAuthoringBundle(
                draft = draft,
                sections = listOf(
                    DraftSection(
                        id = sectionId,
                        draftId = draftId,
                        title = command.sectionTitle,
                        order = 0,
                    ),
                ),
                themes = listOf(
                    DraftTheme(
                        id = themeId,
                        draftId = draftId,
                        sectionId = sectionId,
                        title = command.themeTitle,
                        order = 0,
                    ),
                ),
                lessons = listOf(
                    DraftLesson(
                        id = lessonId,
                        draftId = draftId,
                        themeId = themeId,
                        title = command.lessonTitle,
                        order = 0,
                    ),
                ),
                questions = emptyList(),
            )
            repository.saveDraft(bundle).getOrThrow()
            Result.success(draftId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
