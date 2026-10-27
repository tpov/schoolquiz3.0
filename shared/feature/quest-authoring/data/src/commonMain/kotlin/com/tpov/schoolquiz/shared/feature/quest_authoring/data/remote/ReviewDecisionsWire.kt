package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionCursor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionFilter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue

/**
 * Носитель «Решений» (история 4.17, UX-22): строгая форма серверного вью `quest-review-decisions-v1`.
 *
 * Разбор строг намеренно: расширенное или битое тело — отказ и перечитка, а не выдуманная строка.
 * Границы зеркалят серверные (страница ≤ 60 строк, источники и тексты ограничены) — клиент не
 * доверяет больше, чем сервер обещал.
 */
data class ReviewDecisionsWirePage(
    val entries: List<ReviewDecisionsWireEntry>,
    val hasMore: Boolean,
    val nextCursor: ReviewDecisionCursor?,
)

data class ReviewDecisionsWireEntry(
    val unreadable: Boolean,
    val verdictId: String,
    val submissionId: String,
    val questTitle: String,
    val lessonId: String,
    val lessonTitle: String,
    val stage: String,
    val attempt: Int,
    val revision: Long,
    val model: String,
    val promptVersion: String,
    val sources: List<String>,
    val rationale: String,
    val questionId: String,
    val verdict: String?,
    val reason: String,
    val question: ReviewDecisionsWireQuestion?,
    val markValue: ReviewDecisionMarkValue?,
    val markComment: String,
    val markAdminUid: String,
    val markUpdatedAtMs: Long,
)

data class ReviewDecisionsWireQuestion(
    val type: String,
    val language: String,
    val text: String,
    val payload: String,
)

data class ReviewDecisionsWireMark(
    val value: ReviewDecisionMarkValue,
    val comment: String,
    val updatedAtMs: Long,
)

/** Тело вызова страницы: фильтр и курсор в серверной форме. */
data class ReviewDecisionsWireRequest(
    val filter: ReviewDecisionFilter,
    val cursor: ReviewDecisionCursor?,
)
