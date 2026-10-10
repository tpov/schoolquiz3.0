package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Намерение подать приватный отчёт об ошибке урока (AD-20).
 *
 * Текст — про весь урок; полей вопросов здесь нет по построению. Идентификатор намерения
 * ([id]) назначается в момент нажатия и не меняется ни при одной попытке (AD-2); из него же
 * выводится ключ мутации, поэтому повторная постановка того же отчёта уезжает под тем же ключом.
 */
data class LessonErrorReportSubmission(
    val id: String,
    val ownerUid: String,
    val requestedAtMs: Long,
    val command: SubmitLessonErrorReportCommand,
)

data class SubmitLessonErrorReportCommand(
    val lessonId: String,
    val text: String,
)
