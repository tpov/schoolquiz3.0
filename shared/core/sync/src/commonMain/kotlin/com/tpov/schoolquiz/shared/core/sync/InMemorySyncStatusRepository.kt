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

/**
 * Состояние синхронизации, собранное из очереди и памяти процесса.
 *
 * Счётчики берутся из хранилища очереди — они переживают перезапуск. Время последнего успеха и
 * последняя ошибка живут в памяти: они описывают текущий сеанс, а не аккаунт, и «синхронизировались
 * когда-то до перезапуска» игроку сказать нечестно — приложение этого не знает.
 *
 * Поток пересоздаётся на смену аккаунта (инвариант 8): очередь принадлежит `uid`, и показывать
 * чужие счётчики после переключения нельзя. Для отсутствующего аккаунта отдаются нули, а не пустой
 * поток — иначе подписчик навсегда остался бы без единого значения и показал бы прошлое.
 */
class InMemorySyncStatusRepository(
    private val store: OutboxStore,
    private val currentUidFlow: Flow<String?>,
) : SyncStatusRepository {

    private val lastSuccessAtMs = MutableStateFlow(0L)
    private val lastError = MutableStateFlow<SyncError?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStatus(): Flow<SyncStatus> =
        currentUidFlow
            .flatMapLatest { uid ->
                val counts =
                    if (uid.isNullOrBlank()) flowOf(OutboxCounts()) else store.observeCounts(uid)
                combine(counts, lastSuccessAtMs, lastError) { c, at, error ->
                    SyncStatus(lastSuccessAtMs = at, counts = c, lastError = error)
                }
            }
            .distinctUntilChanged()

    override suspend fun recordSuccess(atMs: Long) {
        lastSuccessAtMs.value = atMs
        // Удачный проход снимает прошлую ошибку: держать её значит показывать игроку жалобу на
        // то, что уже починилось.
        lastError.value = null
    }

    override suspend fun recordFailure(
        error: SyncError,
        atMs: Long,
    ) {
        lastError.value = error
    }
}
