package com.tpov.schoolquiz.android.feature.tournament.presentation.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Типы публичных (редактированных) вопросов — серверная константа `safeQuestion`. */
object TournamentQuestionTypes {
    const val SINGLE_CHOICE = "SingleChoiceRedacted"
    const val MULTIPLE_CHOICE = "MultipleChoiceRedacted"
    const val ORDERING = "OrderingRedacted"
    const val FILL_BLANK = "FillBlankRedacted"
}

/**
 * Полезная нагрузка ответа игрока из публичного (редактированного) вопроса. Ровно то, что приёмник
 * `tournament.SESSION_OP` хранит по `answerPayload`: тип вопроса из [com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestionPayload.type]
 * и выбор игрока по идентификаторам строк — никаких ключей, маппингов и материалов форм здесь
 * нет и быть не может, их в публичной проекции тоже нет.
 */
object TournamentAnswerPayload {
    /** Ответ одиночного выбора: одна выбранная строка. */
    fun singleChoice(selectedId: String) =
        buildJsonObject {
            put("type", "single-choice")
            put("selected", selectedId)
        }

    /** Ответ множественного выбора: выбранные строки в порядке отметки. */
    fun multipleChoice(selectedIds: List<String>) =
        buildJsonObject {
            put("type", "multiple-choice")
            put(
                "selected",
                buildJsonArray { selectedIds.forEach { add(JsonPrimitive(it)) } },
            )
        }

    /** Ответ на упорядочивание: порядок строк, предложенный игроком. */
    fun ordering(orderedIds: List<String>) =
        buildJsonObject {
            put("type", "ordering")
            put(
                "order",
                buildJsonArray { orderedIds.forEach { add(JsonPrimitive(it)) } },
            )
        }

    /** Ответ на заполнение пропусков: текст игрока на каждый пропуск по порядку. */
    fun fillBlank(blanks: List<String>) =
        buildJsonObject {
            put("type", "fill-blank")
            put(
                "blanks",
                buildJsonArray { blanks.forEach { add(JsonPrimitive(it)) } },
            )
        }
}
