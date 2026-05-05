package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

data class ReviewSegmentDecision(
    val questionId: String,
    val segmentKey: String,
    val accepted: Boolean,
)
