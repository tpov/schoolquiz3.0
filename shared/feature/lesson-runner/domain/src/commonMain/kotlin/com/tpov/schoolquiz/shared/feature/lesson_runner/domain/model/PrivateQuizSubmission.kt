package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Подача попытки приватного квиза — замороженное тело мутации `private_quiz.SUBMIT_ATTEMPT`.
 *
 * Форма ответа — сериализованный [UserAnswerLike]: имена полей те, что читает серверный приёмник
 * (`type`/`selected`/`order`/`filled`); перемешанные id Ordering/FillBlank сервер переводит
 * ключами заморозки прогона — клиент их не видит и не должен.
 */
data class PrivateQuizSubmission(
    val served: List<Served>,
    val answers: List<Answer>,
) {
    /** Вопрос, показанный участнику, и его позиция в коде ответа. */
    data class Served(
        val questionId: String,
        val codeAnswerIndex: Int,
    )

    /** Ответ (или его отсутствие — показан и просрочен) одним вопросом подачи. */
    data class Answer(
        val questionId: String,
        val codeAnswerIndex: Int,
        val answerPayload: String?,
        val answeredAtMs: Long,
    )

    init {
        require(served.isNotEmpty()) { "PrivateQuizSubmission.served must not be empty: no denominator" }
        val indexes = served.map { it.codeAnswerIndex }
        require(indexes.size == indexes.toSet().size) { "PrivateQuizSubmission.served names a position twice" }
        require(answers.all { it.answerPayload != null || it.answeredAtMs >= 0 }) {
            "PrivateQuizSubmission.answer carries neither payload nor a timeout mark"
        }
    }
}

/** Ответ одного вопроса в форме сервера; собирается из [UserAnswerDraft] ниже. */
object PrivateQuizAnswerCodec {
    private val json =
        Json {
            // Дискриминатор класса — провод приёмника: `type` ровно там, где его ищет
            // evaluateAnswer ("single-choice"/"multiple-choice"/"ordering"/"fill-blank").
            encodeDefaults = false
            classDiscriminator = "type"
            ignoreUnknownKeys = true
        }

    /**
     * Ответ черновика в JSON подачи: тот же полиморфный `UserAnswer`, что едет у обычной
     * попытки. Непустой черновик даёт ответ; пустой (просроченный без ввода) — `null`,
     * вопрос едет «показан, без ответа».
     */
    fun answerPayload(draft: UserAnswerDraft): String? {
        val answer =
            when (draft) {
                is UserAnswerDraft.SingleChoiceDraft ->
                    UserAnswer.SingleChoiceAnswer(selected = draft.selected)
                is UserAnswerDraft.MultipleChoiceDraft ->
                    if (draft.selected.isEmpty()) return null else UserAnswer.MultipleChoiceAnswer(selected = draft.selected)
                is UserAnswerDraft.OrderingDraft ->
                    if (draft.order.isEmpty()) return null else UserAnswer.OrderingAnswer(order = draft.order)
                is UserAnswerDraft.FillBlankDraft ->
                    if (draft.filled.isEmpty()) return null else UserAnswer.FillBlankAnswer(filled = draft.filled)
                is UserAnswerDraft.SurveyDraft ->
                    if (draft.selected.isEmpty()) return null else UserAnswer.SurveyAnswer(selected = draft.selected)
            }
        return json.encodeToString<UserAnswer>(answer)
    }
}
