package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/**
 * Состояние синхронизации, собранное из очереди и следа прогона.
 *
 * И счётчики, и след лежат в базе и переживают перезапуск: «когда синхронизировались последний раз»
 * — факт про аккаунт, а не про сеанс. Пока след жил в памяти процесса, холодный старт показывал
 * «синхронизации ещё не было» игроку, у которого она прошла минуту назад, — то самое молчание,
 * ради устранения которого состояние и выводится наружу (AD-14).
 *
 * Поток пересоздаётся на смену аккаунта (инвариант 8): и очередь, и след принадлежат `uid`, и
 * показывать чужие числа после переключения нельзя. Для отсутствующего аккаунта отдаются нули, а
 * не пустой поток — иначе подписчик навсегда остался бы без единого значения и показал бы прошлое.
 *
 * Следы раздельные по виду прогона (AD-14): у содержимого, профиля и ручного «Перечитать всё»
 * свои расписания и свои читающие стороны, поэтому и «когда последний раз прошло» у них своё.
 * Общий след означал, что удачный профильный прогон снимает ветвь упавшего контентного, — на
 * экране это выглядело как свежая синхронизация при содержимом суточной давности.
 *
 * Непонятые записи журналов ([recordUnreadableChanges]) остаются в памяти намеренно: число
 * описывает последний проход журнала в этом сеансе, а не аккаунт. После перезапуска ни один журнал
 * ещё не читался, и ноль тут честен — в отличие от нуля на месте времени последнего успеха.
 */
class DefaultSyncStatusRepository(
    private val store: OutboxStore,
    private val trace: SyncRunTraceStore,
    private val currentUidFlow: Flow<String?>,
    private val currentUidProvider: suspend () -> String?,
) : SyncStatusRepository {
    /** Непонятые записи по журналам: у каждого своё число, наружу выходит сумма. */
    private val unreadableByJournal = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Чей сеанс описывают числа непонятых записей. Без него они принадлежали бы всем. */
    private var owner: String? = null

    /** Отличает «аккаунт ещё не видели» от «аккаунта нет»: иначе первая подписка сбрасывала бы счёт. */
    private var ownerKnown = false

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStatus(): Flow<SyncStatus> =
        currentUidFlow
            .flatMapLatest { uid ->
                rememberOwner(uid)
                val counts =
                    if (uid.isNullOrBlank()) flowOf(OutboxCounts()) else store.observeCounts(uid)
                val runs =
                    if (uid.isNullOrBlank()) {
                        flowOf(emptyMap<SyncRunKind, SyncRunTrace>())
                    } else {
                        trace.observeTraces(uid)
                    }
                combine(counts, runs, unreadableByJournal) { c, r, unreadable ->
                    val content = r.traceOf(SyncRunKind.CONTENT)
                    val profile = r.traceOf(SyncRunKind.PROFILE)
                    val resync = r.traceOf(SyncRunKind.RESYNC)
                    SyncStatus(
                        lastSuccessAtMs = content.lastSuccessAtMs,
                        counts = c,
                        lastError = content.lastError,
                        unreadableChanges = unreadable.values.sum(),
                        profileLastSuccessAtMs = profile.lastSuccessAtMs,
                        profileLastError = profile.lastError,
                        resyncLastSuccessAtMs = resync.lastSuccessAtMs,
                        resyncLastError = resync.lastError,
                    )
                }
            }
            .distinctUntilChanged()

    override suspend fun recordSuccess(
        kind: SyncRunKind,
        atMs: Long,
    ) {
        val uid = currentUid() ?: return
        trace.recordSuccess(uid, kind, atMs)
    }

    override suspend fun recordFailure(
        kind: SyncRunKind,
        error: SyncError,
        atMs: Long,
    ) {
        val uid = currentUid() ?: return
        trace.recordFailure(uid, kind, error)
    }

    override suspend fun recordUnreadableChanges(
        journal: String,
        count: Int,
    ) {
        // Замещение внутри журнала, сумма — снаружи. Общее число, которое пишет последний
        // доложивший, показывало бы ноль по журналу урока просто потому, что каталоги чисты.
        unreadableByJournal.update { it + (journal to count) }
    }

    /**
     * След гостя не пишется: он никому не принадлежит.
     *
     * Записать его под пустым ключом значило бы показать это время первому же вошедшему как своё.
     */
    private suspend fun currentUid(): String? = currentUidProvider()?.takeIf { it.isNotBlank() }

    /** Смена аккаунта обнуляет счёт непонятых записей: он описывает проход конкретного игрока. */
    private fun rememberOwner(uid: String?) {
        if (!ownerKnown) {
            ownerKnown = true
            owner = uid
        } else if (uid != owner) {
            owner = uid
            unreadableByJournal.value = emptyMap()
        }
    }
}
