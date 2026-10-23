package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/**
 * Приватный квиз после двери (AD-29/AD-31, эпик 8, история 8.4).
 *
 * Дверь — серверный гейт `startPrivateQuizAttempt`: атомарная проверка ссылки и лимита, запись
 * старта с замороженной идентичностью квиза. Всё, что клиент делает после, — прогон по
 * выданному набору (уже ОБЕЗЛИЧЕННОМУ сервером: ключей в нём нет) и чтение собственного
 * результата, видимость которого решает ТЕКУЩАЯ настройка showResult.
 *
 * Отдельного контроллера режима здесь нет (AD-2): тайминг вопроса ведёт существующий
 * ONLINE-O2 прогон; приватная модель несёт только идентичность, срок и содержимое.
 */

/** Какая дверь и почему закрыта. Отказ входа — типизированное состояние, а не текст (AD-21). */
enum class PrivateQuizEntryRefusal {
    /** Ссылка выключена автором. */
    LINK_DISABLED,

    /** Ссылка истекла: server now ≥ срока. */
    LINK_EXPIRED,

    /** Лимит попыток исчерпан: startedCount ≥ текущего лимита. */
    ATTEMPT_LIMIT_REACHED,

    /** Квиза нет или чужая попытка не отличима от несуществующей. */
    NOT_FOUND,

    /** Квиз изменился после старта: замороженной порции больше не существует. */
    RUN_CONTENT_CHANGED,

    /** Попытка уже завершена: продолжения не существует (AD-29: один заход). */
    ATTEMPT_TERMINAL,

    /** Нет сессии. */
    AUTH_REQUIRED,

    /** Сервер не ответил или отказал немо. */
    UNAVAILABLE,
}

/** Замороженный набор прогона: идентичность, срок и публичные половины вопросов. */
data class PrivateQuizRun(
    val startId: String,
    val startedAtMs: Long,
    /**
     * Срок попытки от ИСХОДНОГО старта: сумма HARD-времён замороженного набора плюс окна
     * AD-31. Восстановление его не продлевает — дедлайн производная от [startedAtMs].
     */
    val deadlineMs: Long,
    /** Вопросы в порядке прогона; payload — публичная половина, без ключей. */
    val questions: List<PrivateQuizRunQuestion>,
) {
    init {
        require(startId.isNotBlank()) { "PrivateQuizRun.startId must not be blank" }
        require(startedAtMs >= 0) { "PrivateQuizRun.startedAtMs must be >= 0" }
        require(deadlineMs >= startedAtMs) { "PrivateQuizRun.deadlineMs must not precede startedAtMs" }
        require(questions.isNotEmpty()) { "PrivateQuizRun.questions must not be empty" }
    }
}

/** Один вопрос прогона. Payload — строка JSON публичной половины; ключа здесь нет и быть не должно. */
data class PrivateQuizRunQuestion(
    val id: String,
    val order: Int,
    val payload: String,
) {
    init {
        require(id.isNotBlank()) { "PrivateQuizRunQuestion.id must not be blank" }
        require(order >= 0) { "PrivateQuizRunQuestion.order must be >= 0" }
    }
}

/** Исход попытки. Терминал терминален: «продолжить позже» у приватного прохода не существует (AD-31). */
enum class PrivateQuizAttemptReason {
    FINISHED,
    TIMEOUT,
}

/**
 * Собственный результат попытки — как его разрешила ТЕКУЩАЯ настройка showResult.
 *
 * [showsScore] == false — только подтверждение завершения: счёт, процент и по-вопросные цифры
 * сервер не прислал, и нулями они не рисуются никогда (AD-21: неизвестное не ноль). true —
 * собственный результат: N/M, процент и свои цифры. Чужого результата не существует при любом
 * флаге.
 */
data class PrivateQuizOwnResult(
    val startId: String,
    val attemptStatus: PrivateQuizAttemptStatus,
    val reason: PrivateQuizAttemptReason?,
    val finishedAtMs: Long?,
    val percentScore: Int?,
    val correctCount: Int?,
    val questionCount: Int?,
    val questionScores: List<Int>?,
) {
    /** Показывать ли счёт: только когда сервер его прислал, а не «показать нули». */
    val showsScore: Boolean
        get() = percentScore != null && correctCount != null && questionCount != null

    init {
        require(startId.isNotBlank()) { "PrivateQuizOwnResult.startId must not be blank" }
        require(percentScore == null || percentScore in 0..100) {
            "PrivateQuizOwnResult.percentScore must be null or 0..100, got $percentScore"
        }
        require((correctCount == null) == (questionCount == null)) {
            "PrivateQuizOwnResult.counts arrive together or not at all"
        }
        require(questionScores == null || (correctCount != null && questionScores.size == questionCount)) {
            "PrivateQuizOwnResult.questionScores must match the question count"
        }
    }
}

/** Статус попытки в проекции результата. RUNNING — терминала ещё нет. */
enum class PrivateQuizAttemptStatus {
    RUNNING,
    TERMINAL,
}

/** Вход в приватный квиз: либо прогон, либо именованный отказ двери. Третьего нет. */
sealed interface PrivateQuizEntry {
    data class Ready(val run: PrivateQuizRun) : PrivateQuizEntry

    data class Refused(val reason: PrivateQuizEntryRefusal) : PrivateQuizEntry
}
