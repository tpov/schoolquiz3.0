package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Намерение предложить правку чужого урока (CAP-27, история 4.23).
 *
 * В теле едут только изменённые вопросы — ровно те, чей верный ответ предложивший отметил в
 * копии; каждый — авторской формы (публичная половина + отметка = полный payload с ключом,
 * который «Факты» сверяет, а одобрение автора применяет). Идентификатор намерения ([id])
 * назначается в момент нажатия (AD-2) и выводит ключ мутации.
 */
data class CopyEditProposal(
    val id: String,
    val ownerUid: String,
    val requestedAtMs: Long,
    val command: SubmitCopyEditProposalCommand,
)

data class SubmitCopyEditProposalCommand(
    val lessonId: String,
    val lessonTitle: String,
    /** Только изменённые вопросы, в авторской форме; на проводе их ждёт белый список 4.21. */
    val questions: List<CopyEditProposalQuestion>,
    /** Идентификаторы изменённых вопросов — подмножество идентификаторов [questions]. */
    val editedQuestionIds: List<String>,
)

data class CopyEditProposalQuestion(
    val id: String,
    val type: String,
    val language: String,
    val languageLevel: Int,
    val difficulty: String,
    val order: Int,
    val text: String,
    val imagePath: String?,
    val payload: String,
)
