package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

// Проводные факты онлайн-перехода next-question по общему контракту ONLINE-O2
// (эпик `epic-ordinary-delivery`, `online-o2-contract.md`; серверная половина —
// `beginLessonQuestionWindow`/`fetchNextLessonQuestion`, история 2.6).
//
// Модели режимно-нейтральны: арену приводит к ним ArenaOnlineO2Adapter, ordinary-онлайн делает то
// же своим адаптером. Ни одна из них не несёт клиентского времени как источника deadline — только
// серверные факты выдачи (§5, замещающих часов нет).

/**
 * Тело события завершения вопроса — запрос открытия окна загрузки (§2–§3).
 *
 * Собирается детерминированно из замороженного снапшота попытки: повтор события с тем же телом
 * открывает/читает то же окно, а не второе (§7); другое тело под той же парой — конфликт сервера
 * (AD-6). `difficulty` едет как есть из снапшота — клиент не назначает сложность серверу.
 */
data class QuestionWindowRequest(
    val attemptId: String,
    val lessonId: String,
    val lessonVersion: Long,
    val completedQuestionIndex: Int,
    val nextQuestionId: String,
    val isHard: Boolean,
) {
    init {
        require(attemptId.isNotBlank()) { "attemptId must not be blank" }
        require(lessonId.isNotBlank()) { "lessonId must not be blank" }
        require(lessonVersion >= 0) { "lessonVersion must be non-negative" }
        require(completedQuestionIndex >= 0) { "completedQuestionIndex must be non-negative" }
        require(nextQuestionId.isNotBlank()) { "nextQuestionId must not be blank" }
    }
}

/**
 * Вид открытого окна из `beginLessonQuestionWindow`: identity перехода и захваченная конфигурация
 * (§3, captured config) — без выдачи. `windowSeconds` и `allocatedSeconds` захвачены сервером при
 * открытии и по ходу попытки не меняются; повтор запроса возвращает те же значения.
 */
data class QuestionWindowView(
    val windowId: String,
    val attemptId: String,
    val completedQuestionIndex: Int,
    val nextQuestionIndex: Int,
    val state: String,
    val windowSeconds: Int,
    val allocatedSeconds: Int,
    val openedAtMs: Long,
    val expiresAtMs: Long,
)

/** Серверная выдача следующего вопроса: кто, когда и с каким исходным deadline (§3, §5). */
data class QuestionDelivery(
    val questionIndex: Int,
    val questionId: String,
    val issuedAtMs: Long,
    val answerDeadlineMs: Long,
) {
    init {
        require(questionIndex >= 0) { "questionIndex must be non-negative" }
        require(questionId.isNotBlank()) { "questionId must not be blank" }
        require(issuedAtMs >= 0) { "issuedAtMs must be non-negative" }
        require(answerDeadlineMs >= issuedAtMs) { "answerDeadlineMs must not precede the issuance" }
    }
}

/**
 * Вердикт запроса выдачи (§3). Первую выдачу провод не различает от повтора — сервер отвечает
 * `status: "DELIVERED"` в обоих случаях, и клиенту это безразлично: и там и там это один и тот же
 * вопрос с одним и тем же серверным deadline и серверным остатком. Вторую выдачу повтор не
 * создаёт; истёкшее без выдачи окно закрывает попытку без ответа (§7).
 */
sealed interface QuestionDeliveryVerdict {
    /** Выдача состоялась (в первый раз или повторно): тот же вопрос, тот же deadline (§3/§7). */
    data class Delivered(
        val windowId: String,
        val delivery: QuestionDelivery,
        /** Серверный остаток времени ответа на момент ответа сервера; неотрицателен. */
        val remainderMs: Long,
        val serverNowMs: Long,
    ) : QuestionDeliveryVerdict

    /** Окно истекло без выдачи: режимный владелец закрывает попытку по своему контракту (§7). */
    data class WindowExpired(
        val windowId: String?,
        val closedAtMs: Long,
        val serverNowMs: Long,
    ) : QuestionDeliveryVerdict
}

/** Типовой отказ поверхности окна: приватные детали транспорта наружу не выходят. */
enum class QuestionWindowFailureKind {
    /** Сессии нет — вызов некому предъявить. */
    ACCOUNT_UNAVAILABLE,

    /** Сессия сменилась на границе вызова, включая A→B→A. */
    ACCOUNT_CHANGED,

    /** Провод не разобран: форма/диапазоны нарушены, «неизвестно» не становится фактом. */
    MALFORMED_RESPONSE,

    /** Сервер отказал по телу: расхождение клиента с контрактом перехода. */
    INVALID_REQUEST,

    /** Остальное серверное: отказ или недоступность без поименованной клиентской причины. */
    SERVER_FAILURE,
}

class QuestionWindowFailure(val kind: QuestionWindowFailureKind) : Exception(kind.name)
