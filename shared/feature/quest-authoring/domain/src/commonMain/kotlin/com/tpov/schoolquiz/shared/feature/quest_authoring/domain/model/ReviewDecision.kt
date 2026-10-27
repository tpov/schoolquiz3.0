package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Строка «Решений» (история 4.17, UX-22): вопрос внутри записи вердикта модели.
 *
 * Единица строки — именно вопрос: отметка админа адресна аудиту одного вопроса, а метаданные
 * записи (модель/промпт/попытка/ревизия, источники, основание) едут с каждой её строкой одним
 * источником — самой неизменяемой записью. [unreadable] — честный след записи, которую сервер
 * разобрать не смог (AD-10): строка стоит на месте, но данных не несёт.
 */
data class ReviewDecision(
    val unreadable: Boolean,
    val verdictId: String,
    val submissionId: String,
    val questTitle: String,
    val lessonId: String,
    val lessonTitle: String,
    val stage: String,
    val attempt: Int,
    val revision: Long,
    val model: String,
    val promptVersion: String,
    val sources: List<String>,
    val rationale: String,
    val questionId: String,
    val verdict: ReviewDecisionVerdict?,
    val reason: String,
    val question: ReviewDecisionQuestion?,
    val mark: ReviewDecisionMark?,
) {
    init {
        require(verdictId.isNotBlank()) { "ReviewDecision.verdictId must not be blank" }
        require(questionId.isNotBlank() || unreadable) { "ReviewDecision.questionId must not be blank" }
    }
}

/** Вердикт модели по вопросу: бинарный, без шкалы — порогов и звёзд больше нет (UX-17). */
enum class ReviewDecisionVerdict {
    PASS,
    FAIL,
}

/** Тело вопроса для предпросмотра: ровно то, что автор подал, без собранных сервером ключей. */
data class ReviewDecisionQuestion(
    val type: String,
    val language: String,
    val text: String,
    val payload: String,
) {
    init {
        require(type.isNotBlank()) { "ReviewDecisionQuestion.type must not be blank" }
        require(language.isNotBlank()) { "ReviewDecisionQuestion.language must not be blank" }
        require(text.isNotBlank()) { "ReviewDecisionQuestion.text must not be blank" }
        require(payload.isNotBlank()) { "ReviewDecisionQuestion.payload must not be blank" }
    }
}

/** Решение админа: ✓/✗ с необязательным комментарием; живёт отдельно от неизменяемого вердикта. */
data class ReviewDecisionMark(
    val value: ReviewDecisionMarkValue,
    val comment: String,
    val adminUid: String,
    val updatedAtMs: Long,
)

enum class ReviewDecisionMarkValue {
    VALID,
    INVALID,
}

/** Курсор страницы: запись вердикта и, при остановке посреди записи, последний рассмотренный вопрос. */
data class ReviewDecisionCursor(
    val verdictId: String,
    val afterQuestionId: String?,
) {
    init {
        require(verdictId.isNotBlank()) { "ReviewDecisionCursor.verdictId must not be blank" }
    }
}

/** Страница «Решений»: ограниченная выборка строк и честный ответ, есть ли ещё. */
data class ReviewDecisionPage(
    val entries: List<ReviewDecision>,
    val hasMore: Boolean,
    val nextCursor: ReviewDecisionCursor?,
)

/** Фильтры вкладки (UX-22): «Без отметки / Верно / Неверно». */
enum class ReviewDecisionFilter {
    UNMARKED,
    VALID,
    INVALID,
}
