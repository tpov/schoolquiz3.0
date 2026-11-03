package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Строка «Предложений» (AD-33, история 4.25, UX-22): то, что сервер отдал своей выборкой прав.
 *
 * Владелец цели видит только доехавшее (CHECK_PASSED и решения); предложивший — свою запись
 * с исходом проверки. [stale] считает сервер; решение по устаревшему всё равно откажет.
 */
data class QuestProposalRow(
    val proposalId: String,
    /** Строка-метка AD-10: запись не разобралась, её место — след, а не выдуманное содержание. */
    val unreadable: Boolean = false,
    val kind: String,
    val status: String,
    val stale: Boolean,
    val targetLessonId: String?,
    val targetThemeId: String?,
    val targetCatalogId: String?,
    val targetQuestId: String?,
    val createdAtMs: Long,
    val checkedAtMs: Long,
    val decidedAtMs: Long,
    val feeNolics: Int,
    val changedQuestions: Int,
    val decision: QuestProposalDecision?,
    val proposalText: String?,
    val proposalOwnVariant: String?,
    val lessonTitle: String?,
    val questionPreviews: List<QuestProposalQuestionPreview>,
)

data class QuestProposalDecision(
    val decision: String,
    val comment: String?,
    val decidedAtMs: Long,
)

data class QuestProposalQuestionPreview(
    val id: String,
    val text: String,
    val edited: Boolean,
)

data class QuestProposalPage(
    val rows: List<QuestProposalRow>,
    val hasMore: Boolean,
    val nextCursor: String?,
)

/** Полка чтения: входящие (владельцу цели) и исходящие (предложившему). */
enum class QuestProposalBox {
    INCOMING,
    OUTGOING,
}
