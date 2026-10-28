package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Намерение предложить свой урок в чужой курс (CAP-27, история 4.24).
 *
 * Целевой путь — каталог → квест → раздел → тема чужого курса; автор курса решает. Урок едет
 * в теле ровно тем, чем собран редактором: вопросы — авторской формы, ключ в теле нужен
 * конвейеру («Факты» сверяет отмеченный ответ) и применению. Идентификатор намерения ([id])
 * назначается в момент нажатия (AD-2) и выводит ключ мутации.
 */
data class NewLessonProposal(
    val id: String,
    val ownerUid: String,
    val requestedAtMs: Long,
    val command: SubmitNewLessonProposalCommand,
)

data class NewLessonProposalTarget(
    val catalogId: String,
    val questId: String,
    val sectionId: String,
    val themeId: String,
)

data class SubmitNewLessonProposalCommand(
    val target: NewLessonProposalTarget,
    val lessonId: String,
    val lessonTitle: String,
    val questions: List<NewLessonProposalQuestion>,
)

data class NewLessonProposalQuestion(
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
