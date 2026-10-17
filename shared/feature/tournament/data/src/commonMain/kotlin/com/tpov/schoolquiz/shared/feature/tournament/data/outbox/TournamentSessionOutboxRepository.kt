package com.tpov.schoolquiz.shared.feature.tournament.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRemoteFailure
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRemoteFailureKind
import kotlinx.coroutines.flow.Flow

/**
 * Очередная половина мутаций сессии соревнования (история 5.9): намерение -> строка очереди,
 * ответ приёмника -> типизированная квитанция.
 *
 * Ставка записи идёт после захвата сессии и перепроверяется после вставки: строка другого
 * владельца не ставится, ответ чужой смены не признаётся — тот же забор смены аккаунта, что у
 * остальных записей очереди, без нового механизма. Повторная постановка того же намерения
 * возвращает лежащую строку (AD-2); изменение тела под тем же ключом — отказ: правка payload —
 * это новая мутация, и ключ ей клиент не подменяет.
 */
class TournamentSessionOutboxRepository(
    private val outbox: OutboxStore,
    private val receipts: MutationReceiptStore,
    private val sessions: OutboxSessionProvider,
    private val parser: TournamentSessionReceiptParser,
    private val clock: () -> Long,
) {
    /**
     * Кладёт намерение в очередь и возвращает лежащую строку. Тот же intent (тот же ключ, то же
     * замороженное тело) — идемпотентно; тот же ключ с иным телом — [Result.failure] с
     * [IllegalStateException] внутри: молча заменить замороженное намерение очередь не позволяет
     * (AD-2), а отказ по испорченному кодеком телу едет той же обёрткой — [Result] не рождает
     * исключений наружу.
     */
    suspend fun enqueue(intent: TournamentSessionIntent): Result<OutboxRecord> {
        val session = sessions.current() ?: return failure(TournamentRemoteFailureKind.ACCOUNT_UNAVAILABLE)
        return try {
            val row = TournamentSessionOutboxCodec.buildRow(intent, session.ownerUid, clock())
            val stored = outbox.enqueue(row)
            // Конфликт замороженного тела — свойство строки, поэтому сверяется ДО перепроверки
            // смены аккаунта: смена под ответом не маскирует испорченный ключ под ACCOUNT_CHANGED.
            if (stored.mutationId == row.mutationId && stored.payload != row.payload) {
                throw IllegalStateException(
                    "Ключ ${row.mutationId} уже несёт другое замороженное тело: " +
                        "намерение изменилось под тем же ключом",
                )
            }
            if (sessions.current() != session) {
                return failure(TournamentRemoteFailureKind.ACCOUNT_CHANGED)
            }
            Result.success(stored)
        } catch (failure: TournamentRemoteFailure) {
            Result.failure(failure)
        } catch (broken: IllegalArgumentException) {
            // Отказ кодека (charset, диапазон, бюджет ответа) — отказ постановки, а не крах.
            Result.failure(broken)
        } catch (conflict: IllegalStateException) {
            // CancellationException едет тем же типом — отмену сквозь Result не тащим.
            if (conflict is kotlinx.coroutines.CancellationException) throw conflict
            Result.failure(conflict)
        }
    }

    /** Сохранённая квитанция намерения; пусто — сервер ещё не отвечал по этому ключу. */
    suspend fun receipt(
        intent: TournamentSessionIntent,
        ownerUid: String,
    ): TournamentSessionReceiptOutcome? =
        receipts
            .find(ownerUid, TournamentSessionOutboxCodec.mutationKey(intent))
            ?.let { parser.parse(it, intent, ownerUid) }

    /** Пометка «не отправлено» на сессии (AD-14): прямое чтение ядра, без нового механизма. */
    fun observeSession(
        ownerUid: String,
        sessionId: String,
    ): Flow<OutboxEntitySync> = outbox.observeEntity(ownerUid, TournamentSessionOutboxCodec.entityRef(sessionId))

    private fun failure(kind: TournamentRemoteFailureKind): Result<OutboxRecord> =
        Result.failure(TournamentRemoteFailure(kind))
}
