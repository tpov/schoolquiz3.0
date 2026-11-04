package com.tpov.schoolquiz.shared.feature.internet.social.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxKeyRepair
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations

/**
 * Правило переписывания ключей недоехавших голосов — опросная половина общей механики
 * [OutboxKeyRepair] (ретро эпика 8, находка 4).
 *
 * Ряды, записанные до поправки §2 (`402c5882`, two-device прогон 18.10), несут ключ без оси
 * участника: `…-q<idx>` вместо `…-q<idx>-u-<uid>`. Такой ряд после обновления отказывает
 * по чужому — общему между аккаунтами — ключу («mutationId belongs to another account») и
 * после попыток карантинится молча: голос не доезжает, участнику никто не сообщает. Переписывание
 * по новой формуле возвращает ряду его собственный ключ: выводится из того же намерения
 * (тройка §2 + владелец строки), сервер его ещё не встречал, а защита от второго голоса —
 * серверная уникальность (participant, series, revision, question) и permanent-receipt.
 *
 * Вызывается участником обычной синхронизации перед проходом очереди: идемпотентно, после
 * первого прохода правило не находит старых форм. Уже карантинные ряды не воскресаются — их
 * отказ показывает наблюдение поверхности опросов (находка 7).
 */
class PollVoteKeyRepair(
    private val repair: OutboxKeyRepair,
) {
    /** Сколько queued-рядов голосов получили ключ новой формы. */
    suspend fun repairQueuedVotes(): Int = repair.rewritePendingKeys(OutboxOperations.SUBMIT_POLL_VOTE, ::migratedKey)

    companion object {
        /**
         * Ключ уже несёт своего участника — менять нечего (`null` оставляет строку как есть).
         * Старая форма кончается `q<индекс>` и суффикса `-u-<uid>` иметь не может, поэтому
         * точное сравнение хвоста различает формы без разбора середины ключа.
         */
        fun migratedKey(
            ownerUid: String,
            mutationId: String,
        ): String? = if (mutationId.endsWith("-u-$ownerUid")) null else "$mutationId-u-$ownerUid"
    }
}
