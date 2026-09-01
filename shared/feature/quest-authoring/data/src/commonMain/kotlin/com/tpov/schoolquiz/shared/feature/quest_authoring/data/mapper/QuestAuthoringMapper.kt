package com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.persistence.DraftLessonEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftSectionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftThemeEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestArenaSubmissionEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentEntity
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentWithQuestions
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLesson
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionValidationState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSection
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSectionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftTheme
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftThemeId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmissionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraft
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftSummary

object QuestAuthoringMapper {
    fun QuestAuthoringBundle.toEntityBundle(): QuestAuthoringEntityBundle =
        QuestAuthoringEntityBundle(
            draft = draft.toEntity(),
            sections = sections.map { it.toEntity() },
            themes = themes.map { it.toEntity() },
            lessons = lessons.map { it.toEntity() },
            questions = questions.map { it.toEntity() },
        )

    fun QuestAuthoringEntityBundle.toDomain(): QuestAuthoringBundle =
        QuestAuthoringBundle(
            draft = draft.toDomain(),
            sections = sections.map { it.toDomain() },
            themes = themes.map { it.toDomain() },
            lessons = lessons.map { it.toDomain() },
            questions = questions.map { it.toDomain() },
        )

    fun QuestDraft.toEntity(): QuestDraftEntity =
        QuestDraftEntity(
            id = id.value,
            ownerUid = ownerUid,
            catalogId = catalogId.value,
            title = title,
            description = description,
            defaultLanguage = defaultLanguage,
            defaultDifficulty = defaultDifficulty.name,
            status = status.name,
            localRevision = localRevision,
            serverRevision = serverRevision,
            publicQuestId = publicQuestId?.value,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
            rejectionReason = rejectionReason,
            isActive = isActive,
        )

    fun QuestDraftEntity.toDomain(): QuestDraft =
        QuestDraft(
            id = QuestDraftId(id),
            ownerUid = ownerUid,
            catalogId = CatalogId(catalogId),
            title = title,
            description = description,
            defaultLanguage = defaultLanguage,
            defaultDifficulty = Difficulty.valueOf(defaultDifficulty),
            status = QuestDraftStatus.valueOf(status),
            localRevision = localRevision,
            serverRevision = serverRevision,
            publicQuestId = publicQuestId?.let(::QuestId),
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
            isActive = isActive,
        )

    fun DraftSection.toEntity(): DraftSectionEntity =
        DraftSectionEntity(
            id = id.value,
            draftId = draftId.value,
            title = title,
            order = order,
        )

    fun DraftSectionEntity.toDomain(): DraftSection =
        DraftSection(
            id = DraftSectionId(id),
            draftId = QuestDraftId(draftId),
            title = title,
            order = order,
        )

    fun DraftTheme.toEntity(): DraftThemeEntity =
        DraftThemeEntity(
            id = id.value,
            draftId = draftId.value,
            sectionId = sectionId.value,
            title = title,
            order = order,
        )

    fun DraftThemeEntity.toDomain(): DraftTheme =
        DraftTheme(
            id = DraftThemeId(id),
            draftId = QuestDraftId(draftId),
            sectionId = DraftSectionId(sectionId),
            title = title,
            order = order,
        )

    fun DraftLesson.toEntity(): DraftLessonEntity =
        DraftLessonEntity(
            id = id.value,
            draftId = draftId.value,
            themeId = themeId.value,
            title = title,
            order = order,
        )

    fun DraftLessonEntity.toDomain(): DraftLesson =
        DraftLesson(
            id = DraftLessonId(id),
            draftId = QuestDraftId(draftId),
            themeId = DraftThemeId(themeId),
            title = title,
            order = order,
        )

    fun DraftQuestion.toEntity(): DraftQuestionEntity =
        DraftQuestionEntity(
            id = id.value,
            draftId = draftId.value,
            lessonId = lessonId.value,
            type = type.name,
            language = language,
            difficulty = difficulty.name,
            order = order,
            text = text,
            imagePath = imagePath,
            payload = payload,
            validationState = validationState.name,
            updatedAtMs = updatedAtMs,
            languageLevel = languageLevel,
        )

    fun DraftQuestionEntity.toDomain(): DraftQuestion =
        DraftQuestion(
            id = DraftQuestionId(id),
            draftId = QuestDraftId(draftId),
            lessonId = DraftLessonId(lessonId),
            type = DraftQuestionType.valueOf(type),
            language = language,
            difficulty = Difficulty.valueOf(difficulty),
            order = order,
            text = text,
            imagePath = imagePath,
            payload = payload,
            validationState = DraftQuestionValidationState.valueOf(validationState),
            updatedAtMs = updatedAtMs,
            languageLevel = languageLevel,
        )

    fun QuestDraftSummaryEntity.toDomain(): QuestDraftSummary =
        QuestDraftSummary(
            id = QuestDraftId(id),
            catalogId = CatalogId(catalogId),
            title = title,
            status = QuestDraftStatus.valueOf(status),
            questionCount = questionCount,
            updatedAtMs = updatedAtMs,
            isActive = isActive,
        )

    fun QuestArenaSubmission.toEntity(): QuestArenaSubmissionEntity =
        QuestArenaSubmissionEntity(
            id = id.value,
            draftId = draftId.value,
            ownerUid = ownerUid,
            localRevision = localRevision,
            requestedAtMs = requestedAtMs,
            lessonIds = lessonIds.map { it.value }.sorted(),
            targetShelf = targetShelf,
            attemptCount = attemptCount,
            lastError = lastError,
        )

    fun QuestArenaSubmissionEntity.toDomain(): QuestArenaSubmission =
        QuestArenaSubmission(
            id = QuestArenaSubmissionId(id),
            draftId = QuestDraftId(draftId),
            ownerUid = ownerUid,
            localRevision = localRevision,
            requestedAtMs = requestedAtMs,
            lessonIds = lessonIds.map(::DraftLessonId).toSet(),
            targetShelf = targetShelf,
            attemptCount = attemptCount,
            lastError = lastError,
        )

    fun PrivateQuestSnapshot.toEntityBundle(): QuestAuthoringEntityBundle =
        QuestAuthoringEntityBundle(
            draft =
                QuestDraftEntity(
                    id = request.draft.id,
                    ownerUid = request.ownerUid,
                    catalogId = request.draft.catalogId,
                    title = request.draft.title,
                    description = request.draft.description,
                    defaultLanguage = request.draft.defaultLanguage,
                    defaultDifficulty = request.draft.defaultDifficulty,
                    status = QuestDraftStatus.SYNCED.name,
                    localRevision = serverRevision,
                    serverRevision = serverRevision,
                    publicQuestId = request.draft.publicQuestId,
                    createdAtMs = request.draft.createdAtMs,
                    updatedAtMs = snapshotUpdatedAt(),
                    isActive = false,
                ),
            sections =
                request.sections.map {
                    DraftSectionEntity(
                        id = it.id,
                        draftId = it.draftId,
                        title = it.title,
                        order = it.order,
                    )
                },
            themes =
                request.themes.map {
                    DraftThemeEntity(
                        id = it.id,
                        draftId = it.draftId,
                        sectionId = it.sectionId,
                        title = it.title,
                        order = it.order,
                    )
                },
            lessons =
                request.lessons.map {
                    DraftLessonEntity(
                        id = it.id,
                        draftId = it.draftId,
                        themeId = it.themeId,
                        title = it.title,
                        order = it.order,
                    )
                },
            questions =
                request.questions.map {
                    DraftQuestionEntity(
                        id = it.id,
                        draftId = it.draftId,
                        lessonId = it.lessonId,
                        type = it.type,
                        language = it.language,
                        difficulty = it.difficulty,
                        order = it.order,
                        text = it.text,
                        imagePath = it.imagePath,
                        payload = it.payload,
                        validationState = DraftQuestionValidationState.SAVED.name,
                        updatedAtMs = it.updatedAtMs,
                        languageLevel = it.languageLevel,
                    )
                },
        )

    fun ReviewAssignmentDto.toEntity(viewerUid: String): ReviewAssignmentEntity =
        ReviewAssignmentEntity(
            id = id,
            ownerUid = viewerUid,
            submissionId = submissionId,
            catalogId = catalogId,
            draftId = draftId,
            questId = questId,
            lessonId = lessonId,
            title = title,
            createdAtMs = createdAtMs,
            taskKinds = taskKinds.sorted(),
            sourceLanguages = sourceLanguages.sorted(),
            newTranslationLanguages = newTranslationLanguages.sorted(),
            reviewLanguages = reviewLanguages.sorted(),
            isTested = checks.isTested,
            testingScore = checks.testingScore,
            isLogicReviewed = checks.isLogicReviewed,
            logicScore = checks.logicScore,
            isTranslationReviewed = checks.isTranslationReviewed,
            translationScore = checks.translationScore,
            translatedLanguages =
                checks.translatedLanguages
                    .toSortedMap()
                    .map { (language, level) -> "$language=$level" },
        )

    fun ReviewAssignmentDto.toEntityWithQuestions(viewerUid: String): ReviewAssignmentWithQuestions =
        ReviewAssignmentWithQuestions(
            assignment = toEntity(viewerUid),
            questions = questions.map { it.toQuestionEntity(viewerUid, id) },
        )

    private fun ArenaQuestionDto.toQuestionEntity(
        viewerUid: String,
        assignmentId: String,
    ): ReviewAssignmentQuestionEntity =
        ReviewAssignmentQuestionEntity(
            ownerUid = viewerUid,
            assignmentId = assignmentId,
            questionId = id,
            draftId = draftId,
            lessonId = lessonId,
            type = type,
            language = language,
            languageLevel = languageLevel,
            difficulty = difficulty,
            order = order,
            text = text,
            imagePath = imagePath,
            payload = payload,
            updatedAtMs = updatedAtMs,
        )

    private fun PrivateQuestSnapshot.snapshotUpdatedAt(): Long =
        maxOf(changedAtMs, request.draft.updatedAtMs)
}
