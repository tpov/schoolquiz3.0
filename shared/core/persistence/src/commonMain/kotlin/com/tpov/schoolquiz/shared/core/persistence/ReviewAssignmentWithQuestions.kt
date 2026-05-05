package com.tpov.schoolquiz.shared.core.persistence

data class ReviewAssignmentWithQuestions(
    val assignment: ReviewAssignmentEntity,
    val questions: List<ReviewAssignmentQuestionEntity>,
)
