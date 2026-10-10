package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

data class SubmitReviewActionCommand(
    val assignmentId: String,
    val lessonId: String,
    val kind: ReviewAssignmentKind,
    val score: Int? = null,
    /** TESTING оценивает каждый вопрос ревизии; состав называет задание (4.6). */
    val questionScores: List<ReviewQuestionScore> = emptyList(),
    val language: String? = null,
    val targetReviewId: String? = null,
    val translatedQuestions: List<ReviewQuestion> = emptyList(),
    val segmentResults: List<ReviewSegmentDecision> = emptyList(),
)
