package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Явное решение по сегменту перевода (CAP-3): `correct` или `error`. Молчаливого одобрения не
 * существует — сегмент без решения остаётся неотмеченным и в подачу не попадает.
 */
enum class ReviewSegmentDecisionKind {
    CORRECT,
    ERROR,
}

data class ReviewSegmentDecision(
    val questionId: String,
    val segmentKey: String,
    val decision: ReviewSegmentDecisionKind,
) {
    /** Сервер хранит и возвращает флаг одобрения — он производён от явного решения. */
    val accepted: Boolean get() = decision == ReviewSegmentDecisionKind.CORRECT
}
