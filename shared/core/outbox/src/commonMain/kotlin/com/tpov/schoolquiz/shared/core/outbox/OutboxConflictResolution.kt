package com.tpov.schoolquiz.shared.core.outbox

/**
 * Разрешение конфликта: принять серверную версию и отправить работу заново (AD-24).
 *
 * Повтор прежней записи здесь невозможен намеренно. Ключ идемпотентности не меняется ни при одной
 * попытке (AD-2) — сервер уже видел его и отверг, и второй раз по тому же ключу не применит
 * ничего. Поэтому решение игрока — это **новая** мутация с новым ключом и с ожидаемой версией,
 * взятой у сервера, а прежняя запись снимается.
 *
 * Функция чистая и хранилища не знает: записывают её результат владеющие фичи, каждая своей
 * транзакцией — вместе со своей половиной изменения (AD-23). Ядро при этом остаётся слепым к
 * смыслу: [OutboxRecord.payload], `operation` и `entityRef` переезжают дословно (AD-7).
 */
object OutboxConflictResolution {

    /**
     * Запись, которой игрок отправляет работу заново поверх версии, названной сервером.
     *
     * @param conflicted запись в [OutboxState.CONFLICT] — другую разрешать нечего.
     * @param nowMs момент решения из единого источника времени. Он новый, а не унаследованный:
     *   возраст записи считается от намерения (AD-1), и разрешение конфликта, родившееся с датой
     *   прежней заявки, могло бы оказаться перезревшим в момент постановки.
     */
    fun replacementFor(
        conflicted: OutboxRecord,
        nowMs: Long,
    ): OutboxRecord {
        require(conflicted.state == OutboxState.CONFLICT) {
            "Only a conflicted record can be resolved, got ${conflicted.state}"
        }
        return conflicted.copy(
            // Ноль означает «строки ещё нет»: ключ присвоит база.
            id = 0L,
            mutationId = OutboxOperations.resolutionKey(conflicted.mutationId, conflicted.serverVersion),
            // Версия, поверх которой игрок согласился отправить заново. Пустой она бывает только
            // если сервер назвал конфликт, но не назвал версию: тогда сверять нечего, и приёмник
            // пропустит мутацию без проверки.
            expectedVersion = conflicted.serverVersion,
            serverVersion = null,
            state = OutboxState.WAITING,
            attemptCount = 0,
            nextRetryAtMs = 0L,
            lastError = null,
            createdAtMs = nowMs,
        )
    }
}
