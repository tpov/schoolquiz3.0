package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.logic

import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopyRow
import kotlinx.serialization.json.Json

/**
 * Отметка верного ответа в редактируемой копии (история 4.23, CAP-27): предложивший отвечает
 * за то, какой ответ правильный, — «Факты» проверяет отметку уже на конвейере 4.21.
 *
 * Формы отметки повторяют публичные половины `question-redaction.js`: у выбора отмечаются
 * варианты, у порядка — порядок строк, у пропусков — кандидат на каждый пропуск. Опросы и
 * легаси-диалекты отметки не имеют: их строки в копии заперты ([LessonEditCopyRow.editable]).
 */
sealed interface EditCopyMark {
    data class SingleChoice(val correctOptionId: String) : EditCopyMark

    data class MultipleChoice(val correctOptionIds: Set<String>) : EditCopyMark

    /** Полный отмеченный порядок строк — он и есть ответ порядка. */
    data class Ordering(val orderedItemIds: List<String>) : EditCopyMark

    /** Отмеченный кандидат на каждый пропуск (идентификаторы пропусков — из публичной половины). */
    data class FillBlank(val correctCandidateByBlank: Map<String, String>) : EditCopyMark
}

/**
 * Отмеченная строка копии — авторская форма вопроса: публичная половина плюс отметка = полный
 * payload с ключом. Сериализуется тем же движком, каким публикация пишет документы, — форма
 * предложения совпадает с формой применённой редакции.
 *
 * Опросы и запертые строки отметки не образуют: [`buildMarkedQuestionOrThrow`] отказывает, а не
 * собирает вопрос без ключа.
 */
object EditCopyMarking {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Собирает авторскую форму отмеченного вопроса. Идентификаторы вариантов/кандидатов —
     * переизданные копией (`ri-0…`, `rc-0…`): применение пишет новую редакцию, выданные банки
     * попыток не переписываются (AD-3), так что смена идентификаторов внутри payload безопасна.
     */
    fun buildMarkedQuestionOrThrow(row: LessonEditCopyRow, mark: EditCopyMark): QuestionContent {
        val redacted = json.decodeFromString<RedactedQuestionContent>(row.payload)
        val difficulty = runCatching { Difficulty.valueOf(row.difficulty) }.getOrDefault(Difficulty.EASY)
        return when (redacted) {
            is RedactedQuestionContent.SingleChoice ->
                QuestionContent.SingleChoice(
                    id = row.id,
                    difficulty = difficulty,
                    text = redacted.text,
                    imageUrl = redacted.imageUrl,
                    options = redacted.options.map { QuestionContent.Option(OptionId(it.id), it.text) },
                    correctOptionId = OptionId(
                        (mark as? EditCopyMark.SingleChoice)?.correctOptionId
                            ?: throw IllegalArgumentException("question ${row.id} needs the marked option"),
                    ),
                )
            is RedactedQuestionContent.MultipleChoice -> {
                val correctOptionIds = (mark as? EditCopyMark.MultipleChoice)?.correctOptionIds
                    ?: throw IllegalArgumentException("question ${row.id} needs the marked options")
                QuestionContent.MultipleChoice(
                    id = row.id,
                    difficulty = difficulty,
                    text = redacted.text,
                    imageUrl = redacted.imageUrl,
                    options = redacted.options.map { QuestionContent.Option(OptionId(it.id), it.text) },
                    correctOptionIds = correctOptionIds.mapTo(HashSet()) { OptionId(it) },
                )
            }
            is RedactedQuestionContent.Ordering -> {
                val orderedItemIds = (mark as? EditCopyMark.Ordering)?.orderedItemIds
                    ?: throw IllegalArgumentException("question ${row.id} needs the marked order")
                val textById = redacted.items.associate { it.id to it.text }
                QuestionContent.Ordering(
                    id = row.id,
                    difficulty = difficulty,
                    text = redacted.text,
                    imageUrl = redacted.imageUrl,
                    items = orderedItemIds.map { itemId ->
                        QuestionContent.OrderItem(OptionId(itemId), textById[itemId] ?: throw IllegalArgumentException(
                            "question ${row.id} marks unknown item $itemId",
                        ))
                    },
                )
            }
            is RedactedQuestionContent.FillBlank -> {
                val marked = (mark as? EditCopyMark.FillBlank)?.correctCandidateByBlank
                    ?: throw IllegalArgumentException("question ${row.id} needs the marked blanks")
                QuestionContent.FillBlank(
                    id = row.id,
                    difficulty = difficulty,
                    text = redacted.text,
                    imageUrl = redacted.imageUrl,
                    blanks = redacted.blanks.map { blankId ->
                        val candidateId = marked[blankId]
                            ?: throw IllegalArgumentException("question ${row.id} blank $blankId is not marked")
                        QuestionContent.Blank(BlankId(blankId), CandidateId(candidateId))
                    },
                    candidates = redacted.candidates.map { QuestionContent.Candidate(CandidateId(it.id), it.text) },
                )
            }
        }
    }

    /** Отметка в авторский JSON — та же форма, которой сервер пишет применённую редакцию. */
    fun encodePayload(question: QuestionContent): String = json.encodeToString(QuestionContent.serializer(), question)
}
