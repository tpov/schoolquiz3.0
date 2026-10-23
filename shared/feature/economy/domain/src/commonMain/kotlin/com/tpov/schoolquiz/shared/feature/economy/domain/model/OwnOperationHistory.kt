package com.tpov.schoolquiz.shared.feature.economy.domain.model

/**
 * Собственная история операций — разрешённая проекция economy (AD-30/UX-20, история 8.6).
 *
 * Поля зафиксированы контрактом ux-o10-own-ledger-projection-contract.md ДО адаптера:
 * identity, дата, повод, знаковое движение, ресурс, статус. Чего в записи нет и не должно
 * появиться: технических полей книги (`sourceId`, `details`), токенов, order IDs — проекция
 * не раскрывает секретов и не исполняет денежных команд.
 */
data class OperationHistoryRecord(
    val id: String,
    val atMs: Long,
    val reason: String,
    val currency: String,
    /** Знаковое движение: положительное — доход, отрицательное — расход. */
    val delta: Long,
    val status: String,
    /** Привязка к уроку, когда она есть в строке книги; иначе null. */
    val lessonId: String?,
) {
    /** Доход или расход — фильтры Все/Начисления/Расходы читают знак, а не домысливают его. */
    val isIncome: Boolean
        get() = delta > 0

    init {
        require(id.isNotBlank()) { "OperationHistoryRecord.id must not be blank" }
        require(atMs >= 0) { "OperationHistoryRecord.atMs must be >= 0, got $atMs" }
        require(reason.isNotBlank()) { "OperationHistoryRecord.reason must not be blank" }
        require(currency.isNotBlank()) { "OperationHistoryRecord.currency must not be blank" }
        require(delta != 0L) { "OperationHistoryRecord.delta must not be zero: a zero movement is not a movement" }
    }
}

/**
 * Неразрешённый билет — pending. Виден, но доходом не считается никогда: удержание ещё не
 * превратилось ни в расход, ни в возврат (UX-20: «Pending is not earned»).
 */
data class PendingOperation(
    val ticketId: String,
    val atMs: Long,
    val currency: String,
    val heldAmount: Long,
) {
    init {
        require(ticketId.isNotBlank()) { "PendingOperation.ticketId must not be blank" }
        require(atMs >= 0) { "PendingOperation.atMs must be >= 0" }
        require(heldAmount > 0) { "PendingOperation.heldAmount must be positive, got $heldAmount" }
    }
}

/** Одна ограниченная страница истории с продолжением. */
data class OwnHistoryPage(
    val entries: List<OperationHistoryRecord>,
    val pending: List<PendingOperation>,
    /** Курсор продолжения; null — страниц больше нет. Пустая страница с курсором не выдаётся. */
    val nextCursor: String?,
) {
    init {
        require(entries.isNotEmpty() || nextCursor == null) {
            "OwnHistoryPage: an empty page never continues"
        }
    }
}
