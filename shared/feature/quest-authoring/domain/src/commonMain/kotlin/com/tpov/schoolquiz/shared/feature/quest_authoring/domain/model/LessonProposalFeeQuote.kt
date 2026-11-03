package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Цитата платы предложения (UX-23, история 4.22): то, что вход «Предложить автору» показывает
 * до отправки. Показ, а не власть — платой остаётся расчёт приёмки 4.21 из тела реальной подачи.
 *
 * [kind] повторяет вид предложения (REPORT/COPY_EDIT/NEW_LESSON); для REPORT принятый тариф — 0.
 */
data class LessonProposalFeeQuote(
    val kind: String,
    val feeNolics: Int,
    val tariffRevision: Int,
)
