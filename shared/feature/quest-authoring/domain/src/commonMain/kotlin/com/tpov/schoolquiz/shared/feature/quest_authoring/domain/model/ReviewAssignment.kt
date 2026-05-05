package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

data class ReviewAssignment(
    val id: String,
    val submissionId: String,
    val ownerUid: String,
    val catalogId: String,
    val draftId: String,
    val questId: String,
    val lessonId: String,
    val title: String,
    val createdAtMs: Long,
    val taskKinds: Set<ReviewAssignmentKind>,
    val sourceLanguages: Set<String>,
    val newTranslationLanguages: Set<String>,
    val reviewLanguages: Set<String>,
    val checks: ReviewChecks,
    val questions: List<ReviewQuestion>,
)
