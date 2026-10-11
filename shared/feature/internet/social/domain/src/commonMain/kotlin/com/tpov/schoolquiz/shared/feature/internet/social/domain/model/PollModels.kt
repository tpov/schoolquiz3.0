package com.tpov.schoolquiz.shared.feature.internet.social.domain.model

/**
 * Домен опросов (UX-O7, история 8.2 эпика `epic-social-features`).
 *
 * Идентичность голоса — тройка серия/revision/вопрос (контракт §2); серия — единый draft,
 * опубликованная revision — новая идентичность (§6.2). Correctness, сложности и уроковой
 * иерархии у опроса нет (AD-28): вопрос — текст, режим и 2–8 вариантов.
 */

/** Режим вопроса: один вариант или набор. Кардинальность режима — часть определения серии. */
enum class PollMode(val wire: String) {
    SINGLE("SINGLE"),
    MULTIPLE("MULTIPLE"),
    ;

    companion object {
        fun of(wire: String): PollMode = entries.firstOrNull { it.wire == wire }
            ?: throw IllegalArgumentException("Unsupported poll mode: $wire")
    }
}

/** Вариант вопроса: идентичен optionId внутри revision, без цен и правильности. */
data class PollOption(
    val optionId: String,
    val text: String,
)

/** Вопрос серии: один вопрос — один полный экран (UX-15). */
data class PollQuestion(
    val questionIndex: Int,
    val text: String,
    val mode: PollMode,
    val options: List<PollOption>,
)

/** Строка списка серий: опубликованная серия с собственным прогрессом читателя. */
data class PollSeriesSummary(
    val seriesId: String,
    val title: String,
    val revisionId: Long,
    val questionCount: Int,
    /** Вопросов текущей revision, на которые читатель уже ответил (не отметок — §5). */
    val answeredCount: Int,
    val own: Boolean,
)

/** Собственный принятый голос: ответ вью на «а я голосовал?» — не локальная догадка (AD-8). */
data class PollOwnVote(
    val selectedOptionIds: List<String>,
    val acceptedAtMs: Long,
)

/**
 * Один серверный снапшот счётов (§5): счёт по каждому варианту + знаменатель — участники,
 * ответившие на вопрос. Проценты считает презентация; сумма multi-choice может превышать 100.
 * null — распределение скрыто (собственного принятого голоса нет), и это не нулевой счёт.
 */
data class PollDistribution(
    val optionCounts: Map<String, Int>,
    val respondentCount: Int,
)

/** Состояние одного вопроса серии: содержание + свой голос + снапшот (или скрытие). */
data class PollQuestionState(
    val seriesId: String,
    val title: String,
    val own: Boolean,
    val revisionId: Long,
    val questionCount: Int,
    val questionIndex: Int,
    val question: PollQuestion,
    val ownVote: PollOwnVote?,
    val distribution: PollDistribution?,
)
