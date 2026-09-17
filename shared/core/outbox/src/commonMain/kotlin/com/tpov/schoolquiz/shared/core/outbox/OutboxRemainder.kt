package com.tpov.schoolquiz.shared.core.outbox

/**
 * Что осталось у владельца в очереди — числом на каждое из пяти состояний.
 *
 * Отдельный тип, а не переиспользованный [OutboxCounts]: у сводки для экрана (AD-14) четыре числа,
 * и `СИНХРОННАЯ` из неё исключена намеренно — она не «ожидает», она хранит ключ. Смене аккаунта
 * нужен пятый: удаляются все пять состояний ([OutboxStore.forget]), значит и предупреждать надо
 * про все пять. Пока остаток считался выборкой «что поедет» ([OutboxStore.countPending]), игрок с
 * конфликтом, карантином или синхронным ключом переключался без единого вопроса, а его записи
 * исчезали молча.
 */
data class OutboxRemainder(
    val waiting: Int = 0,
    val waitingPrecondition: Int = 0,
    val conflicted: Int = 0,
    val quarantined: Int = 0,
    val synchronous: Int = 0,
) {
    /** Сколько строк исчезнет при переключении. */
    val total: Int get() = waiting + waitingPrecondition + conflicted + quarantined + synchronous

    /** Не осталось ничего — переключение можно делать молча. */
    val isEmpty: Boolean get() = total == 0

    /** Складывается, когда владельцев, чьи строки лежат в таблице, оказалось несколько. */
    operator fun plus(other: OutboxRemainder) =
        OutboxRemainder(
            waiting = waiting + other.waiting,
            waitingPrecondition = waitingPrecondition + other.waitingPrecondition,
            conflicted = conflicted + other.conflicted,
            quarantined = quarantined + other.quarantined,
            synchronous = synchronous + other.synchronous,
        )

    companion object {
        val NONE = OutboxRemainder()
    }
}

/**
 * Кто и что оставил в очереди — вопрос смены аккаунта, а не отправки.
 *
 * Порт отдельный от [OutboxStore] по двум причинам. Первая: смысл. `dueRecords` и `countPending`
 * отвечают на «что берётся в выборку», и остаток по ним считать нельзя — выборка видит два
 * состояния из пяти. Вторая: `owners` — единственный вопрос ко всей таблице, а не к одному
 * владельцу, и в контракте очереди, где каждый метод начинается с `ownerUid`, он выглядел бы
 * чужим.
 */
interface OutboxRemainderSource {
    /** Сколько строк [ownerUid] осталось в каждом из пяти состояний. */
    suspend fun remainderOf(ownerUid: String): OutboxRemainder

    /**
     * Все `uid`, чьи строки лежат в таблице сейчас.
     *
     * Нужен ровно там, где действующей сессии нет: `auth.currentUser == null` не значит «терять
     * нечего» — строки прежнего владельца никуда не делись, и спросить про них надо у таблицы, а
     * не у аутентификации.
     */
    suspend fun ownersWithRecords(): List<String>
}
