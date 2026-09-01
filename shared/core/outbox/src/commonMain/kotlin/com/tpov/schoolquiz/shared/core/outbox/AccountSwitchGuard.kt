package com.tpov.schoolquiz.shared.core.outbox

/**
 * Что делать с очередью перед сменой аккаунта (AD-8).
 *
 * Сегодня этот путь реален и ничем не обслужен: вход через Google при коллизии делает
 * `signInWithCredential`, а не привязку, и записи очереди с прежним `uid` после переключения будут
 * получать отказ сервера вечно — их владельца больше нет.
 *
 * Слить надо **до** переключения: после него прежнего `uid` уже не узнать, а запись принадлежит
 * тому, кто её создал, и под другим аккаунтом не отправляется.
 */
class AccountSwitchGuard(
    private val engine: OutboxEngine,
    private val store: OutboxStore,
) {
    /**
     * Пытается отправить всё, что ждёт у [uid], и говорит, что осталось.
     *
     * Не бросает: неудача слива — это не повод не дать сменить аккаунт, это повод предупредить.
     * Решение принимает игрок, а не очередь.
     */
    suspend fun flushBefore(uid: String): SwitchReadiness {
        if (uid.isBlank()) return SwitchReadiness.CLEAN
        runCatching { engine.drain(uid) }
        val left = runCatching { store.countPending(uid) }.getOrDefault(UNKNOWN_REMAINDER)
        return when {
            left == 0 -> SwitchReadiness.CLEAN
            left == UNKNOWN_REMAINDER -> SwitchReadiness(unsent = UNKNOWN_REMAINDER, isKnown = false)
            else -> SwitchReadiness(unsent = left, isKnown = true)
        }
    }

    private companion object {
        /** Даже сосчитать не удалось — база не ответила. */
        const val UNKNOWN_REMAINDER = -1
    }
}

/**
 * Насколько безопасно менять аккаунт прямо сейчас.
 *
 * @property unsent сколько действий останется неотправленными. `-1`, если сосчитать не вышло.
 * @property isKnown удалось ли вообще узнать число. Незнание — тоже повод предупредить, и оно не
 *   то же самое, что «ничего не осталось».
 */
data class SwitchReadiness(
    val unsent: Int,
    val isKnown: Boolean,
) {
    /** Можно менять молча: всё уехало. */
    val isClean: Boolean get() = isKnown && unsent == 0

    /** Игрока надо предупредить словами, что последние действия могут не сохраниться. */
    val needsWarning: Boolean get() = !isClean

    companion object {
        val CLEAN = SwitchReadiness(unsent = 0, isKnown = true)
    }
}
