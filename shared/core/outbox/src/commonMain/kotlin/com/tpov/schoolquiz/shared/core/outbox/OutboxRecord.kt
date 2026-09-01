package com.tpov.schoolquiz.shared.core.outbox

/**
 * Одна отложенная мутация.
 *
 * Таблица одна на все типы действий (AD-5): новый тип не требует правки схемы. Вариант «таблица
 * на тип» отвергнут — типов будет много, а история миграций в проекте недостоверна.
 *
 * @property id локальный ключ строки.
 * @property mutationId ключ идемпотентности. Рождается вместе с намерением игрока и не меняется
 *   ни при одной попытке (AD-2); правка [payload] — это новая мутация с новым ключом, а не та же.
 * @property ownerUid чей это аккаунт. Запись не отправляется под другим (AD-8).
 * @property operation тип действия.
 * @property payload тело, строкой — ядро в него не смотрит.
 * @property entityRef ссылка на сущность для пометки «не отправлено» в интерфейсе (AD-14).
 *   Непрозрачна для ядра: разбирает её та фича, которая запись создала.
 * @property expectedVersion версия, с которой мутация уходила (AD-24). Пусто для неверсионируемых.
 * @property serverVersion версия, которую назвал сервер, отвергая мутацию по конфликту (AD-24).
 *   Пусто, пока конфликта не было. Хранится числом, а не в [lastError]: разрешение конфликта
 *   опирается на конкретную версию, а разбирать её из текста запрещено (AD-15).
 * @property state единственный источник ответа «что с этой записью».
 * @property attemptCount сколько раз уже пробовали.
 * @property nextRetryAtMs когда пробовать снова.
 * @property lastError причина последней неудачи — для журнала и для показа.
 * @property createdAtMs когда игрок совершил действие. От него считается предельный возраст.
 */
data class OutboxRecord(
    val id: Long,
    val mutationId: String,
    val ownerUid: String,
    val operation: String,
    val payload: String,
    val state: OutboxState,
    val createdAtMs: Long,
    val entityRef: String? = null,
    val expectedVersion: Long? = null,
    val serverVersion: Long? = null,
    val attemptCount: Int = 0,
    val nextRetryAtMs: Long = 0L,
    val lastError: String? = null,
) {
    init {
        require(mutationId.isNotBlank()) { "mutationId must not be blank" }
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        require(operation.isNotBlank()) { "operation must not be blank" }
        require(attemptCount >= 0) { "attemptCount must be non-negative" }
        require(createdAtMs >= 0) { "createdAtMs must be non-negative" }
        require(nextRetryAtMs >= 0) { "nextRetryAtMs must be non-negative" }
    }
}

/**
 * Что с записью происходит сейчас. Набор закрытый (AD-5).
 *
 * Кодировать состояние переполнением [OutboxRecord.attemptCount] или значением
 * [OutboxRecord.nextRetryAtMs] запрещено: оба способа сливают разные состояния в один счётчик, а
 * наружу надо отдавать их по отдельности (AD-14).
 */
enum class OutboxState {
    /** В выборке: ждёт своей очереди на отправку. */
    WAITING,

    /** В выборке: предусловие ещё не выполнено другой мутацией из этой же очереди (AD-27). */
    WAITING_PRECONDITION,

    /** Вне выборки: версия устарела, ждёт решения игрока (AD-24). Не терминальное. */
    CONFLICT,

    /** Вне выборки, терминальное: повторять бессмысленно. Последствие разбирает фича (AD-28). */
    QUARANTINED,

    /**
     * Вне выборки всегда: хранит только ключ синхронной мутации, чтобы после смерти процесса
     * спросить сервер об её судьбе, а не предлагать действие второй раз (AD-2).
     */
    SYNCHRONOUS,
    ;

    /** Берётся ли запись в выборку на отправку. */
    val isPending: Boolean get() = this == WAITING || this == WAITING_PRECONDITION

    /** Кончилась ли история записи. Конфликт не терминален — по нему ещё будет решение. */
    val isTerminal: Boolean get() = this == QUARANTINED
}
