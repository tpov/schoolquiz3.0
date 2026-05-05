package com.tpov.schoolquiz.server.functions.review

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest

fun QuestArenaSubmissionRequest.toReviewChecks(): ReviewChecks =
    ReviewChecks(
        isTested = review.isTested,
        testingScore = review.testingScore,
        isLogicReviewed = review.isLogicReviewed,
        logicScore = review.logicScore,
        isTranslationReviewed = review.isTranslationReviewed,
        translationScore = review.translationScore,
        translatedLanguages = review.translatedLanguages.ifEmpty { questions.questionLanguages() },
    )

fun QuestArenaSubmissionRequest.toAdminLessonTasks(createdAtMs: Long): List<AdminReviewLessonTask> {
    return lessons.map { lesson ->
        val lessonQuestions = questions.filter { it.lessonId == lesson.id }
        AdminReviewLessonTask(
            id = "${submissionId}_${lesson.id}",
            submissionId = submissionId,
            ownerUid = ownerUid,
            catalogId = draft.catalogId,
            draftId = draftId,
            questId = draft.id,
            lessonId = lesson.id,
            title = lesson.title,
            createdAtMs = createdAtMs,
            changedAtMs = createdAtMs,
            checks = review.toChecks(lessonQuestions.questionLanguages()),
            questions = lessonQuestions,
            sourceLanguages = lessonQuestions.mapTo(linkedSetOf()) { it.language.trim().lowercase() },
        )
    }
}

private fun List<ArenaQuestionDto>.questionLanguages(): Map<String, Int> =
    this
        .groupBy { it.language }
        .mapValues { (_, questions) -> questions.maxOf { it.languageLevel } }

private fun ArenaReviewDto.toChecks(fallbackLanguages: Map<String, Int>): ReviewChecks =
    ReviewChecks(
        isTested = isTested,
        testingScore = testingScore,
        isLogicReviewed = isLogicReviewed,
        logicScore = logicScore,
        isTranslationReviewed = isTranslationReviewed,
        translationScore = translationScore,
        translatedLanguages = translatedLanguages.ifEmpty { fallbackLanguages },
    )

@Suppress("unused")
fun ArenaReviewDto.toChecks(): ReviewChecks =
    ReviewChecks(
        isTested = isTested,
        testingScore = testingScore,
        isLogicReviewed = isLogicReviewed,
        logicScore = logicScore,
        isTranslationReviewed = isTranslationReviewed,
        translationScore = translationScore,
        translatedLanguages = translatedLanguages,
    )

fun ReviewChecks.toArenaReviewDto(): ArenaReviewDto =
    ArenaReviewDto(
        isTested = isTested,
        testingScore = testingScore,
        isLogicReviewed = isLogicReviewed,
        logicScore = logicScore,
        isTranslationReviewed = isTranslationReviewed,
        translationScore = translationScore,
        translatedLanguages = translatedLanguages,
    )
