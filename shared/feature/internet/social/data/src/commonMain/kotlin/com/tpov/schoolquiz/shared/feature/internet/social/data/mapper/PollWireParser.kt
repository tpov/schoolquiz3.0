package com.tpov.schoolquiz.shared.feature.internet.social.data.mapper

import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollDistribution
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollMode
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollOption
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollOwnVote
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestion
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollQuestionState
import com.tpov.schoolquiz.shared.feature.internet.social.domain.model.PollSeriesSummary

/**
 * Строгий декодер view-конвертов `poll-views-v1` (UX-O7 §5).
 *
 * Версия view обязана совпасть. «Скрыто» и «нулевой счёт» — разные состояния: распределение
 * читается только присутствующим объектом, его отсутствие не изображается нулями (AD-8).
 */
object PollWireParser {
    const val VIEW_VERSION = "poll-views-v1"

    fun seriesList(data: Any?): List<PollSeriesSummary> {
        val root = requiredObject(data, "poll series list")
        requireVersion(root)
        return root.list(ENTRIES).map(::summary)
    }

    fun questionState(data: Any?): PollQuestionState {
        val root = requiredObject(data, "poll question state")
        requireVersion(root)
        val questionRaw = requiredObject(root[QUESTION], "poll question")
        val question = PollQuestion(
            questionIndex = questionRaw.requiredLong(QUESTION_INDEX).toInt(),
            text = questionRaw.requiredText(TEXT),
            mode = PollMode.of(questionRaw.requiredText(MODE)),
            options = questionRaw.list(OPTIONS).map { optionRaw ->
                val option = requiredObject(optionRaw, "poll option")
                PollOption(
                    optionId = option.requiredText(OPTION_ID),
                    text = option.requiredText(TEXT),
                )
            },
        )
        return PollQuestionState(
            seriesId = root.requiredText(SERIES_ID),
            title = root.requiredText(TITLE),
            own = root.requiredBoolean(OWN),
            revisionId = root.requiredLong(REVISION_ID),
            questionCount = root.requiredLong(QUESTION_COUNT).toInt(),
            questionIndex = root.requiredLong(QUESTION_INDEX).toInt(),
            question = question,
            ownVote = root[OWN_VOTE]?.let { ownRaw ->
                val own = requiredObject(ownRaw, "own vote")
                PollOwnVote(
                    selectedOptionIds = own.list(SELECTED_OPTION_IDS).map { value ->
                        (value as? String ?: error("selectedOptionIds must be strings"))
                    },
                    acceptedAtMs = own.requiredLong(ACCEPTED_AT_MS),
                )
            },
            distribution = root[DISTRIBUTION]?.let { distributionRaw ->
                val distribution = requiredObject(distributionRaw, "poll distribution")
                val counts = requiredObject(distribution[OPTION_COUNTS], "option counts")
                PollDistribution(
                    optionCounts = counts.entries.associate { (key, value) ->
                        key.toString() to (value as? Number ?: error("option count is not a number")).toInt()
                    },
                    respondentCount = distribution.requiredLong(RESPONDENT_COUNT).toInt(),
                )
            },
        )
    }

    private fun summary(raw: Any?): PollSeriesSummary {
        val entry = requiredObject(raw, "poll series summary")
        return PollSeriesSummary(
            seriesId = entry.requiredText(SERIES_ID),
            title = entry.requiredText(TITLE),
            revisionId = entry.requiredLong(REVISION_ID),
            questionCount = entry.requiredLong(QUESTION_COUNT).toInt(),
            answeredCount = entry.requiredLong(ANSWERED_COUNT).toInt(),
            own = entry.requiredBoolean(OWN),
        )
    }

    private fun requiredObject(
        data: Any?,
        what: String,
    ): Map<*, *> = data as? Map<*, *> ?: error("$what response is not an object")

    private fun requireVersion(root: Map<*, *>) {
        check(root.string(VIEW_VERSION_FIELD) == VIEW_VERSION) { "Unsupported poll view version" }
    }

    private fun Map<*, *>.list(key: String): List<Any?> = this[key] as? List<*> ?: error("Field $key is not a list")

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.requiredText(key: String): String {
        val value = string(key) ?: error("Field $key is missing")
        check(value.isNotBlank()) { "Field $key is blank" }
        return value
    }

    private fun Map<*, *>.requiredLong(key: String): Long {
        // Android callable SDK кладёт JSON-числа не только в Long (живой прогон 4.3) — сверяю
        // через Number, как и остальные парсеры семейства.
        val value = this[key] as? Number ?: error("Field $key is not a number")
        return value.toLong()
    }

    private fun Map<*, *>.requiredBoolean(key: String): Boolean =
        this[key] as? Boolean ?: error("Field $key is not a boolean")

    private const val VIEW_VERSION_FIELD = "viewVersion"
    private const val ENTRIES = "entries"
    private const val SERIES_ID = "seriesId"
    private const val TITLE = "title"
    private const val REVISION_ID = "revisionId"
    private const val QUESTION_COUNT = "questionCount"
    private const val ANSWERED_COUNT = "answeredCount"
    private const val OWN = "own"
    private const val QUESTION_INDEX = "questionIndex"
    private const val QUESTION = "question"
    private const val TEXT = "text"
    private const val MODE = "mode"
    private const val OPTIONS = "options"
    private const val OPTION_ID = "optionId"
    private const val OWN_VOTE = "ownVote"
    private const val SELECTED_OPTION_IDS = "selectedOptionIds"
    private const val ACCEPTED_AT_MS = "acceptedAtMs"
    private const val DISTRIBUTION = "distribution"
    private const val OPTION_COUNTS = "optionCounts"
    private const val RESPONDENT_COUNT = "respondentCount"
}
