package com.tpov.schoolquiz.server.functions.review

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto

data class TrustedProfile(
    val uid: String,
    val testerLevel: Int = 0,
    val adminLevel: Int = 0,
    val translatorLevel: Int = 0,
    val developerLevel: Int = 0,
    val knownLanguages: Set<String> = emptySet(),
) {
    init {
        require(uid.isNotBlank()) { "TrustedProfile.uid must not be blank" }
        require(testerLevel >= 0) { "TrustedProfile.testerLevel must be >= 0" }
        require(adminLevel >= 0) { "TrustedProfile.adminLevel must be >= 0" }
        require(translatorLevel >= 0) { "TrustedProfile.translatorLevel must be >= 0" }
        require(developerLevel >= 0) { "TrustedProfile.developerLevel must be >= 0" }
        require(knownLanguages.none { it.isBlank() }) {
            "TrustedProfile.knownLanguages must not contain blank values"
        }
    }
}

data class ReviewChecks(
    val isTested: Boolean = false,
    val testingScore: Double? = null,
    val isLogicReviewed: Boolean = false,
    val logicScore: Double? = null,
    val isTranslationReviewed: Boolean = false,
    val translationScore: Int? = null,
    val translatedLanguages: Map<String, Int> = emptyMap(),
) {
    val hasTestingResult: Boolean
        get() = isTested || testingScore != null

    val hasLogicResult: Boolean
        get() = isLogicReviewed || logicScore != null

    val isReadyForTranslation: Boolean
        get() = hasTestingResult && hasLogicResult

    init {
        require(testingScore == null || testingScore in 0.0..3.0) {
            "ReviewChecks.testingScore must be null or in 0.0..3.0"
        }
        require(logicScore == null || logicScore in 0.0..3.0) {
            "ReviewChecks.logicScore must be null or in 0.0..3.0"
        }
        require(translationScore == null || translationScore in 0..100) {
            "ReviewChecks.translationScore must be null or in 0..100"
        }
        require(translatedLanguages.keys.none { it.isBlank() }) {
            "ReviewChecks.translatedLanguages must not contain blank language keys"
        }
        require(translatedLanguages.values.all { it >= 0 }) {
            "ReviewChecks.translatedLanguages values must be >= 0"
        }
    }
}

data class AdminReviewLessonTask(
    val id: String,
    val submissionId: String,
    val ownerUid: String,
    val catalogId: String,
    val draftId: String,
    val questId: String,
    val lessonId: String,
    val title: String,
    val createdAtMs: Long,
    val changedAtMs: Long,
    val checks: ReviewChecks,
    val questions: List<ArenaQuestionDto>,
    val sourceLanguages: Set<String> = questions.mapTo(linkedSetOf()) { it.language.trim().lowercase() },
) {
    val availableLanguages: Set<String>
        get() = checks.translatedLanguages.keys.ifEmpty { questions.map { it.language }.toSet() }

    init {
        require(id.isNotBlank()) { "AdminReviewLessonTask.id must not be blank" }
        require(submissionId.isNotBlank()) { "AdminReviewLessonTask.submissionId must not be blank" }
        require(ownerUid.isNotBlank()) { "AdminReviewLessonTask.ownerUid must not be blank" }
        require(catalogId.isNotBlank()) { "AdminReviewLessonTask.catalogId must not be blank" }
        require(draftId.isNotBlank()) { "AdminReviewLessonTask.draftId must not be blank" }
        require(questId.isNotBlank()) { "AdminReviewLessonTask.questId must not be blank" }
        require(lessonId.isNotBlank()) { "AdminReviewLessonTask.lessonId must not be blank" }
        require(title.isNotBlank()) { "AdminReviewLessonTask.title must not be blank" }
        require(createdAtMs >= 0) { "AdminReviewLessonTask.createdAtMs must be >= 0" }
        require(changedAtMs >= 0) { "AdminReviewLessonTask.changedAtMs must be >= 0" }
        require(sourceLanguages.none { it.isBlank() }) {
            "AdminReviewLessonTask.sourceLanguages must not contain blank values"
        }
    }
}

data class ArenaReviewConfig(
    val requiredLanguages: Set<String> = emptySet(),
    val updatedAtMs: Long = 0L,
) {
    init {
        require(requiredLanguages.none { it.isBlank() }) {
            "ArenaReviewConfig.requiredLanguages must not contain blank values"
        }
        require(updatedAtMs >= 0L) { "ArenaReviewConfig.updatedAtMs must be >= 0" }
    }
}

data class ReviewAssignmentChange(
    val assignmentId: String,
    val lessonId: String,
    val changedAtMs: Long,
) {
    init {
        require(assignmentId.isNotBlank()) { "ReviewAssignmentChange.assignmentId must not be blank" }
        require(lessonId.isNotBlank()) { "ReviewAssignmentChange.lessonId must not be blank" }
        require(changedAtMs >= 0L) { "ReviewAssignmentChange.changedAtMs must be >= 0" }
    }
}

data class TranslationTargets(
    val sourceLanguages: Set<String>,
    val newTranslationLanguages: Set<String>,
    val reviewLanguages: Set<String>,
) {
    val hasAny: Boolean
        get() = newTranslationLanguages.isNotEmpty() || reviewLanguages.isNotEmpty()
}

enum class ReviewTaskKind {
    TESTING,
    LOGIC,
    TRANSLATION,
    TRANSLATION_REVIEW,
}

data class ReviewSegmentResult(
    val questionId: String,
    val segmentKey: String,
    val accepted: Boolean,
) {
    init {
        require(questionId.isNotBlank()) { "ReviewSegmentResult.questionId must not be blank" }
        require(segmentKey.isNotBlank()) { "ReviewSegmentResult.segmentKey must not be blank" }
    }
}

data class ReviewRecord(
    val id: String,
    val lessonId: String,
    val kind: ReviewTaskKind,
    val reviewerUid: String,
    val reviewerLevelAtSubmit: Int,
    val score: Int? = null,
    val language: String? = null,
    val targetReviewId: String? = null,
    val createdAtMs: Long,
    val acceptedByServer: Boolean,
    val segmentResults: List<ReviewSegmentResult> = emptyList(),
    val translatedQuestions: List<ArenaQuestionDto> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "ReviewRecord.id must not be blank" }
        require(lessonId.isNotBlank()) { "ReviewRecord.lessonId must not be blank" }
        require(reviewerUid.isNotBlank()) { "ReviewRecord.reviewerUid must not be blank" }
        require(reviewerLevelAtSubmit >= 0) { "ReviewRecord.reviewerLevelAtSubmit must be >= 0" }
        require(score == null || score in 1..3) { "ReviewRecord.score must be null or in 1..3" }
        require(language == null || language.isNotBlank()) { "ReviewRecord.language must be null or non-blank" }
        require(targetReviewId == null || targetReviewId.isNotBlank()) {
            "ReviewRecord.targetReviewId must be null or non-blank"
        }
        require(createdAtMs >= 0L) { "ReviewRecord.createdAtMs must be >= 0" }
    }
}

data class SubmitReviewAction(
    val assignmentId: String,
    val lessonId: String,
    val kind: ReviewTaskKind,
    val score: Int? = null,
    val language: String? = null,
    val targetReviewId: String? = null,
    val translatedQuestions: List<ArenaQuestionDto> = emptyList(),
    val segmentResults: List<ReviewSegmentResult> = emptyList(),
) {
    init {
        require(assignmentId.isNotBlank()) { "SubmitReviewAction.assignmentId must not be blank" }
        require(lessonId.isNotBlank()) { "SubmitReviewAction.lessonId must not be blank" }
        require(score == null || score in 1..3) { "SubmitReviewAction.score must be null or in 1..3" }
        require(language == null || language.isNotBlank()) { "SubmitReviewAction.language must be null or non-blank" }
    }
}

data class ReviewerReputationDelta(
    val reviewerUid: String,
    val points: Int,
) {
    init {
        require(reviewerUid.isNotBlank()) { "ReviewerReputationDelta.reviewerUid must not be blank" }
    }
}

data class ReviewActionResult(
    val record: ReviewRecord,
    val aggregate: ReviewChecks,
    val reviewerDeltas: List<ReviewerReputationDelta> = emptyList(),
)

data class ReviewSubmissionResult(
    val submissionId: String,
    val privateQuestPath: String,
    val adminLessonTaskPaths: List<String>,
)
