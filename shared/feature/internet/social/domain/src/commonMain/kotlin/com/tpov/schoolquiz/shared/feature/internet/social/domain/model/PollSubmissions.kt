package com.tpov.schoolquiz.shared.feature.internet.social.domain.model

/**
 * Намерения опросов, уходящие общей очередью (AD-6/AD-7): публикация серии и голос.
 *
 * `id` назначается в момент нажатия и больше не меняется (AD-2); ключ мутации выводится
 * из идентичности действия (§2 — тройка для голоса, серия + ожидаемая revision для публикации),
 * поэтому повторная постановка того же намерения даёт тот же ключ (UxO7PollVoteIdentityContractTest).
 */

/** Содержание авторского черновика: до публикации правится свободно (§6.2), ключа у него нет. */
data class PollQuestionDraft(
    val text: String,
    val mode: PollMode,
    /** Тексты вариантов; optionId рождается при сборке тела публикации. */
    val optionTexts: List<String>,
)

data class PollSeriesDraft(
    val title: String,
    val questions: List<PollQuestionDraft>,
)

/** Собранное тело публикации: вопросIndex — позиции, optionId — из нумерации. */
data class PollPublishCommand(
    val seriesId: String,
    val title: String,
    /** Revision, поверх которой автор строил edit; 0 — создание. */
    val expectedRevisionId: Long,
    val questions: List<PollQuestion>,
)

data class PollPublishSubmission(
    val id: String,
    val ownerUid: String,
    val requestedAtMs: Long,
    val command: PollPublishCommand,
)

data class PollVoteCommand(
    val seriesId: String,
    val revisionId: Long,
    val questionIndex: Int,
    /** Один подтверждённый непустой набор — не список отметок (AD-28). */
    val selectedOptionIds: List<String>,
)

data class PollVoteSubmission(
    val id: String,
    val ownerUid: String,
    val requestedAtMs: Long,
    /** Момент фиксации набора кнопкой «Проголосовать» — замораживается в теле (§2). */
    val confirmedAtMs: Long,
    val command: PollVoteCommand,
)
