package com.tpov.schoolquiz.shared.feature.internet.social.domain.use_case

import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollOption
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollPublishCommand
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollPublishSubmission
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestion
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionDraft
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesDraft
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesSummary
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollVoteCommand
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollVoteSubmission
import com.tpov.schoolquiz.shared.feature.internet.social.domain.provider.PollsIdProvider
import com.tpov.schoolquiz.shared.feature.internet.social.domain.provider.PollsTimestampProvider
import com.tpov.schoolquiz.shared.feature.internet.social.domain.repository.PollsRepository
import kotlinx.coroutines.CancellationException

/**
 * Чтение списка опубликованных серий (§5): серверная проекция с собственным прогрессом.
 * Право здесь не проверяется — опубликованные серии видят все вошедшие.
 */
class ListPollSeriesUseCase(
    private val repository: PollsRepository,
) {
    suspend operator fun invoke(pageSize: Int = 20): Result<List<PollSeriesSummary>> =
        try {
            require(pageSize in 1..50) { "pageSize must be within the server page bounds" }
            repository.listSeries(pageSize)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
}

/** Чтение состояния одного вопроса: содержание + свой голос + снапшот или скрытие (§5). */
class GetPollQuestionStateUseCase(
    private val repository: PollsRepository,
) {
    suspend operator fun invoke(
        seriesId: String,
        questionIndex: Int,
    ): Result<PollQuestionState> =
        try {
            require(seriesId.matches(SERIES_ID)) { "seriesId has the server charset" }
            require(questionIndex >= 0) { "questionIndex must not be negative" }
            repository.questionState(seriesId, questionIndex)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }

    private companion object {
        val SERIES_ID = Regex("[A-Za-z0-9_-]{1,64}")
    }
}

/**
 * Голос уходит в общую очередь, а не в callable (AD-6/AD-7): тело — один замороженный набор
 * (§2), ключ выводится из тройки идентичности, retry — не второй голос (§6.1). Набор обязан
 * быть непустым и без дублей; кардинальность режима проверяется здесь и сервером.
 */
class SubmitPollVoteUseCase(
    private val repository: PollsRepository,
    private val idProvider: PollsIdProvider,
    private val timestampProvider: PollsTimestampProvider,
) {
    suspend operator fun invoke(
        ownerUid: String,
        command: PollVoteCommand,
    ): Result<Unit> =
        try {
            require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
            require(command.seriesId.matches(SERIES_ID)) { "seriesId has the server charset" }
            require(command.revisionId >= 1) { "revisionId must be a published revision" }
            require(command.questionIndex >= 0) { "questionIndex must not be negative" }
            val selected = command.selectedOptionIds
            require(selected.isNotEmpty()) { "a confirmed vote carries a nonempty choice set" }
            require(selected.size <= 8) { "a choice set fits the 2..8 option bound" }
            require(selected.all { it.matches(OPTION_ID) }) { "optionId has the server charset" }
            require(selected.toSet().size == selected.size) { "a choice set carries no duplicates" }
            repository.queueVote(
                PollVoteSubmission(
                    id = idProvider.nextId("poll-vote"),
                    ownerUid = ownerUid,
                    requestedAtMs = timestampProvider.nowMs(),
                    confirmedAtMs = timestampProvider.nowMs(),
                    command = command,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }

    private companion object {
        val SERIES_ID = Regex("[A-Za-z0-9_-]{1,64}")
        val OPTION_ID = Regex("[A-Za-z0-9_-]{1,64}")
    }
}

/**
 * Публикация серии (§6.2): единый draft собирается здесь в тело приёмника — questionIndex
 * становятся позициями, optionId рождаются из нумерации с осью вопроса (`q<index>-opt-<n>`):
 * у двух разных вопросов одной серии optionId не пересекаются, и счёт сервера не смешивает
 * чужие голоса даже при равных текстах вариантов (ретро эпика 8, находка 1). Серия без
 * вопросов и вопрос с меньшим числом вариантов, чем 2, существовать не могут (AD-28).
 */
class PublishPollSeriesUseCase(
    private val repository: PollsRepository,
    private val idProvider: PollsIdProvider,
    private val timestampProvider: PollsTimestampProvider,
) {
    suspend operator fun invoke(
        ownerUid: String,
        seriesId: String,
        draft: PollSeriesDraft,
        expectedRevisionId: Long,
    ): Result<Unit> =
        try {
            require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
            require(seriesId.matches(SERIES_ID)) { "seriesId has the server charset" }
            val title = draft.title.trim()
            require(title.isNotEmpty() && title.length <= 200) { "series title is required (<= 200)" }
            require(draft.questions.isNotEmpty() && draft.questions.size <= 50) { "series needs 1..50 questions" }
            val questions =
                draft.questions.mapIndexedNotNull { index, question ->
                    question.toQuestion(index)
                }
            require(questions.size == draft.questions.size) { "every question carries 2..8 named options" }
            repository.queuePublish(
                PollPublishSubmission(
                    id = idProvider.nextId("poll-publish"),
                    ownerUid = ownerUid,
                    requestedAtMs = timestampProvider.nowMs(),
                    command =
                        PollPublishCommand(
                            seriesId = seriesId,
                            title = title,
                            expectedRevisionId = expectedRevisionId,
                            questions = questions,
                        ),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }

    private fun PollQuestionDraft.toQuestion(index: Int): PollQuestion? {
        val text = this.text.trim()
        if (text.isEmpty() || text.length > 500) return null
        val options =
            optionTexts
                .mapIndexed { optionIndex, optionText -> optionText.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { optionIndex, optionText ->
                    PollOption(optionId = "q$index-opt-${optionIndex + 1}", text = optionText.take(200))
                }
        if (options.size < 2 || options.size > 8) return null
        return PollQuestion(
            questionIndex = index,
            text = text.take(500),
            mode = mode,
            options = options,
        )
    }

    private companion object {
        val SERIES_ID = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
