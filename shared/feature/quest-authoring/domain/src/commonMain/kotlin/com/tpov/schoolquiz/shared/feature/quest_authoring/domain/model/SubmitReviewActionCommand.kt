package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

data class SubmitReviewActionCommand(
    val assignmentId: String,
    val lessonId: String,
    val kind: ReviewAssignmentKind,
    val score: Int? = null,
    val language: String? = null,
    val targetReviewId: String? = null,
    val translatedQuestions: List<ReviewQuestion> = emptyList(),
    val segmentResults: List<ReviewSegmentDecision> = emptyList(),
)
