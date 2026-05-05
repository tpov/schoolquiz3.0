package com.tpov.schoolquiz.server.functions.review

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest

object ReviewDocumentBuilder {
    fun privateDocuments(request: QuestArenaSubmissionRequest): Map<String, Map<String, Any?>> {
        val documents = linkedMapOf<String, Map<String, Any?>>()
        val ownerUid = request.ownerUid
        val catalogId = request.draft.catalogId
        val questId = request.draft.id
        val sectionsById = request.sections.associateBy { it.id }
        val themesById = request.themes.associateBy { it.id }
        val lessonsById = request.lessons.associateBy { it.id }

        documents[ReviewFirestorePaths.privateCatalog(ownerUid, catalogId)] =
                mapOf(
                    "id" to catalogId,
                    "ownerUid" to ownerUid,
                    "updatedAtMs" to request.draft.updatedAtMs,
                    "changedAtMs" to request.requestedAtMs,
            )
        documents[ReviewFirestorePaths.privateQuest(ownerUid, catalogId, questId)] =
            mapOf(
                "id" to questId,
                "draftId" to request.draftId,
                "submissionId" to request.submissionId,
                "ownerUid" to ownerUid,
                "catalogId" to catalogId,
                "title" to request.draft.title,
                "description" to request.draft.description,
                "defaultLanguage" to request.draft.defaultLanguage,
                "defaultDifficulty" to request.draft.defaultDifficulty,
                "publicQuestId" to request.draft.publicQuestId,
                "createdAtMs" to request.draft.createdAtMs,
                "localRevision" to request.localRevision,
                "updatedAtMs" to request.draft.updatedAtMs,
                "changedAtMs" to request.requestedAtMs,
                "review" to request.review.toDocument(),
            )
        documents[ReviewFirestorePaths.privateSyncChange(ownerUid, catalogId, questId)] =
            mapOf(
                "id" to questId,
                "type" to "quest",
                "catalogId" to catalogId,
                "questId" to questId,
                "changedAtMs" to request.requestedAtMs,
            )

        request.sections.forEach { section ->
            documents[ReviewFirestorePaths.privateSection(ownerUid, catalogId, questId, section.id)] =
                mapOf(
                    "id" to section.id,
                    "draftId" to section.draftId,
                    "title" to section.title,
                    "order" to section.order,
                )
        }
        request.themes.forEach { theme ->
            documents[
                ReviewFirestorePaths.privateTheme(
                    ownerUid = ownerUid,
                    catalogId = catalogId,
                    questId = questId,
                    sectionId = theme.sectionId,
                    themeId = theme.id,
                ),
            ] =
                mapOf(
                    "id" to theme.id,
                    "draftId" to theme.draftId,
                    "sectionId" to theme.sectionId,
                    "title" to theme.title,
                    "order" to theme.order,
                )
        }
        request.lessons.forEach { lesson ->
            val theme = requireNotNull(themesById[lesson.themeId]) { "Theme ${lesson.themeId} not found" }
            requireNotNull(sectionsById[theme.sectionId]) { "Section ${theme.sectionId} not found" }
            documents[
                ReviewFirestorePaths.privateLesson(
                    ownerUid = ownerUid,
                    catalogId = catalogId,
                    questId = questId,
                    sectionId = theme.sectionId,
                    themeId = theme.id,
                    lessonId = lesson.id,
                ),
            ] =
                mapOf(
                    "id" to lesson.id,
                    "draftId" to lesson.draftId,
                    "themeId" to lesson.themeId,
                    "title" to lesson.title,
                    "order" to lesson.order,
                )
        }
        request.questions.forEach { question ->
            val lesson = requireNotNull(lessonsById[question.lessonId]) { "Lesson ${question.lessonId} not found" }
            val theme = requireNotNull(themesById[lesson.themeId]) { "Theme ${lesson.themeId} not found" }
            documents[
                ReviewFirestorePaths.privateQuestion(
                    ownerUid = ownerUid,
                    catalogId = catalogId,
                    questId = questId,
                    sectionId = theme.sectionId,
                    themeId = theme.id,
                    lessonId = lesson.id,
                    questionId = question.id,
                ),
            ] = question.toDocument()
        }
        return documents
    }

    fun adminDocuments(tasks: List<AdminReviewLessonTask>): Map<String, Map<String, Any?>> {
        val documents = linkedMapOf<String, Map<String, Any?>>()
        tasks.forEach { task ->
            documents[ReviewFirestorePaths.adminLesson(task.lessonId)] =
                mapOf(
                    "id" to task.lessonId,
                    "submissionId" to task.submissionId,
                    "ownerUid" to task.ownerUid,
                    "catalogId" to task.catalogId,
                    "draftId" to task.draftId,
                    "questId" to task.questId,
                    "title" to task.title,
                    "createdAtMs" to task.createdAtMs,
                    "changedAtMs" to task.changedAtMs,
                    "availableLanguages" to task.availableLanguages.sorted(),
                    "sourceLanguages" to task.sourceLanguages.sorted(),
                    "testingScore" to task.checks.testingScore,
                    "logicScore" to task.checks.logicScore,
                    "translationScore" to task.checks.translationScore,
                    "translatedLanguages" to task.checks.translatedLanguages,
                    "isTested" to task.checks.isTested,
                    "isLogicReviewed" to task.checks.isLogicReviewed,
                    "isTranslationReviewed" to task.checks.isTranslationReviewed,
                    "checks" to task.checks.toDocument(),
                    "questionCount" to task.questions.size,
                )
            documents[ReviewFirestorePaths.adminQuest(task.lessonId, task.questId)] =
                mapOf(
                    "id" to task.questId,
                    "lessonId" to task.lessonId,
                    "ownerUid" to task.ownerUid,
                    "title" to task.title,
                    "checks" to task.checks.toDocument(),
                )
            task.questions.forEach { question ->
                documents[ReviewFirestorePaths.adminQuestion(task.lessonId, task.questId, question.id)] =
                    question.toDocument()
            }
            documents[ReviewFirestorePaths.adminSyncChange(task.id)] =
                mapOf(
                    "id" to task.id,
                    "assignmentId" to task.id,
                    "lessonId" to task.lessonId,
                    "changedAtMs" to task.changedAtMs,
                )
        }
        return documents
    }

    private fun ReviewChecks.toDocument(): Map<String, Any?> =
        mapOf(
            "isTested" to isTested,
            "testingScore" to testingScore,
            "isLogicReviewed" to isLogicReviewed,
            "logicScore" to logicScore,
            "isTranslationReviewed" to isTranslationReviewed,
            "translationScore" to translationScore,
            "translatedLanguages" to translatedLanguages,
        )

    private fun ArenaReviewDto.toDocument(): Map<String, Any?> =
        mapOf(
            "isTested" to isTested,
            "testingScore" to testingScore,
            "isLogicReviewed" to isLogicReviewed,
            "logicScore" to logicScore,
            "isTranslationReviewed" to isTranslationReviewed,
            "translationScore" to translationScore,
            "translatedLanguages" to translatedLanguages,
        )

    private fun ArenaQuestionDto.toDocument(): Map<String, Any?> =
        mapOf(
            "id" to id,
            "draftId" to draftId,
            "lessonId" to lessonId,
            "type" to type,
            "language" to language,
            "languageLevel" to languageLevel,
            "difficulty" to difficulty,
            "order" to order,
            "text" to text,
            "imagePath" to imagePath,
            "payload" to payload,
            "updatedAtMs" to updatedAtMs,
        )
}
