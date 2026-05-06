package com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaLessonDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaSectionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaThemeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionValidationState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle

object QuestArenaSubmissionRequestMapper {
    fun QuestArenaSubmission.toRequest(bundle: QuestAuthoringBundle): QuestArenaSubmissionRequest {
        val targetLessons = bundle.lessons.filter { it.id in lessonIds }
        val targetThemeIds = targetLessons.mapTo(linkedSetOf()) { it.themeId }
        val targetThemes = bundle.themes.filter { it.id in targetThemeIds }
        val targetSectionIds = targetThemes.mapTo(linkedSetOf()) { it.sectionId }
        val targetSections = bundle.sections.filter { it.id in targetSectionIds }
        val savedTargetQuestions =
            bundle.questions
                .filter { it.lessonId in lessonIds }
                .filter { it.validationState == DraftQuestionValidationState.SAVED && it.payload != null }

        return QuestArenaSubmissionRequest(
            submissionId = id.value,
            draftId = draftId.value,
            ownerUid = ownerUid,
            localRevision = localRevision,
            requestedAtMs = requestedAtMs,
            draft =
                ArenaDraftDto(
                    id = bundle.draft.id.value,
                    catalogId = bundle.draft.catalogId.value,
                    title = bundle.draft.title,
                    description = bundle.draft.description,
                    defaultLanguage = bundle.draft.defaultLanguage,
                    defaultDifficulty = bundle.draft.defaultDifficulty.name,
                    publicQuestId = bundle.draft.publicQuestId?.value,
                    createdAtMs = bundle.draft.createdAtMs,
                    updatedAtMs = bundle.draft.updatedAtMs,
                ),
            sections =
                targetSections.map {
                    ArenaSectionDto(
                        id = it.id.value,
                        draftId = it.draftId.value,
                        title = it.title,
                        order = it.order,
                    )
                },
            themes =
                targetThemes.map {
                    ArenaThemeDto(
                        id = it.id.value,
                        draftId = it.draftId.value,
                        sectionId = it.sectionId.value,
                        title = it.title,
                        order = it.order,
                    )
                },
            lessons =
                targetLessons.map {
                    ArenaLessonDto(
                        id = it.id.value,
                        draftId = it.draftId.value,
                        themeId = it.themeId.value,
                        title = it.title,
                        order = it.order,
                    )
                },
            questions =
                savedTargetQuestions
                    .map {
                        ArenaQuestionDto(
                            id = it.id.value,
                            draftId = it.draftId.value,
                            lessonId = it.lessonId.value,
                            type = it.type.name,
                            language = it.language,
                            languageLevel = it.languageLevel,
                            difficulty = it.difficulty.name,
                            order = it.order,
                            text = it.text,
                            imagePath = it.imagePath,
                            payload = requireNotNull(it.payload),
                            updatedAtMs = it.updatedAtMs,
                        )
                    },
            review =
                ArenaReviewDto(
                    translatedLanguages =
                        savedTargetQuestions
                            .groupBy { it.language }
                            .mapValues { (_, questions) -> questions.maxOf { it.languageLevel } },
                ),
            targetShelf = targetShelf,
            targetLessonIds = lessonIds.mapTo(linkedSetOf()) { it.value },
        )
    }
}
