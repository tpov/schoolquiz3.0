package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Намерение подать «Сообщить об ошибке» как предложение автору (CAP-6/27, история 4.22).
 *
 * Текст — про весь урок; «свой вариант» — свободный текст по форме REPORT (4.21), вопросов и
 * ответов в теле нет по построению. Идентификатор намерения ([id]) назначается в момент нажатия
 * и не меняется ни при одной попытке (AD-2); из него же выводится ключ мутации.
 */
data class ErrorReportProposal(
    val id: String,
    val ownerUid: String,
    val requestedAtMs: Long,
    val command: SubmitErrorReportProposalCommand,
)

data class SubmitErrorReportProposalCommand(
    val lessonId: String,
    val text: String,
    /** Опциональный собственный вариант — свободный текст, не редактор вопроса (правка — 4.23). */
    val ownVariant: String?,
)
