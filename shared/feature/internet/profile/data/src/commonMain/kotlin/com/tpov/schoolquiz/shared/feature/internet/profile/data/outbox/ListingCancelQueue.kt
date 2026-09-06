package com.tpov.schoolquiz.shared.feature.internet.profile.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ListingCancelSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Очередь снятий с продажи глазами профиля: положить намерение и спросить, что с ним.
 *
 * Одна на имена и логотипы, потому что у обоих одинаковая половина: локальной нет (AD-25), в
 * очередь кладётся ключ лота, а ответ строке лота читается из очереди по ссылке на сущность
 * (AD-14). Ядро в тело не смотрит и ссылку не разбирает (AD-7) — обе стороны ссылки здесь.
 *
 * Auth-scoped: чей лот, того и очередь. Владелец читается из потока, а не снимком, чтобы после
 * смены аккаунта строка не показывала чужое снятие.
 */
class ListingCancelQueue(
    private val outbox: OutboxStore,
    private val currentUidFlow: () -> Flow<String?>,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /** «Найти старое → положить новое» — два шага; замок не даёт двум нажатиям положить две записи. */
    private val lock = Mutex()

    /**
     * Кладёт снятие в очередь. Сервер не зовётся.
     *
     * Второе нажатие, пока первое ещё не уехало, — то же намерение, а не второе: записи уже
     * лежащей и ждущей хватает (AD-2). Карантинная запись прежнего снятия снимается вместе с
     * постановкой нового: она терминальна и держится только как пометка на строке (AD-28), и
     * новое нажатие по той же строке эту пометку и снимает. Пока она оставалась лежать, она
     * возвращалась на строку, стоило новому снятию доехать и удалиться (AD-4): самой свежей
     * записью по ссылке снова оказывался старый отказ.
     */
    suspend fun enqueue(
        entityRef: String,
        row: (ownerUid: String, nowMs: Long) -> OutboxRecord,
    ) {
        val uid = requireNotNull(currentUidFlow().first()) { "Снять лот с продажи может только вошедший в аккаунт" }
        lock.withLock {
            if (outbox.findByEntityRef(uid, entityRef)?.state?.isPending == true) return
            withdrawAll(uid, entityRef)
            outbox.enqueue(row(uid, nowMs()))
        }
    }

    /**
     * Забывает отказ по лоту — когда лот выставлен заново.
     *
     * Отказ снятия говорил «лота нет». Выставленный заново лот есть, и пометка «уже продан» на
     * нём — неправда, а с ней и всё, что по ней решается. Ждущее снятие не трогается: это живое
     * намерение игрока, и оно уедет.
     */
    suspend fun forgetRefusal(entityRef: String) {
        val uid = currentUidFlow().first() ?: return
        lock.withLock {
            var refused = outbox.findByEntityRef(uid, entityRef)?.takeIf { it.state == OutboxState.QUARANTINED }
            while (refused != null) {
                outbox.remove(refused.id)
                refused = outbox.findByEntityRef(uid, entityRef)?.takeIf { it.state == OutboxState.QUARANTINED }
            }
        }
    }

    /** Снимает записи по ссылке одну за другой: выборка отдаёт по одной, а лежать может не одна. */
    private suspend fun withdrawAll(
        uid: String,
        entityRef: String,
    ) {
        var stale = outbox.findByEntityRef(uid, entityRef)
        while (stale != null) {
            outbox.remove(stale.id)
            stale = outbox.findByEntityRef(uid, entityRef)
        }
    }

    /** Что очередь знает про лот по его ссылке — для пометки на строке. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(entityRef: String): Flow<ListingCancelSync?> =
        currentUidFlow()
            .flatMapLatest { uid ->
                // Без аккаунта чужую очередь не читают (AD-8): ответ тот же, что у пустой очереди,
                // и это явное значение, а не молчание потока.
                if (uid == null) flowOf(OutboxEntitySync.None) else outbox.observeEntity(uid, entityRef)
            }
            .map { sync ->
                when (sync) {
                    OutboxEntitySync.None -> null
                    is OutboxEntitySync.Queued ->
                        when (sync.state) {
                            OutboxState.WAITING, OutboxState.WAITING_PRECONDITION -> ListingCancelSync.QUEUED
                            OutboxState.QUARANTINED -> ListingCancelSync.REFUSED
                            // Лот не версионируется, конфликта у него не бывает; синхронный ключ — не его.
                            OutboxState.CONFLICT, OutboxState.SYNCHRONOUS -> null
                        }
                }
            }
            .distinctUntilChanged()
}
