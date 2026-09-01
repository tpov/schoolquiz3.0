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
    private val unreadableChanges = MutableStateFlow(0)

    /** Чей аккаунт описывают время успеха и последняя ошибка. Без него они принадлежали бы всем. */
    private var owner: String? = null

    /** Отличает «аккаунт ещё не видели» от «аккаунта нет»: без этого первая же подписка сбрасывала бы состояние. */
    private var ownerKnown = false

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStatus(): Flow<SyncStatus> =
        currentUidFlow
            .flatMapLatest { uid ->
                // Смена аккаунта обнуляет и время успеха, и ошибку: они описывают проход
                // конкретного игрока, и показывать их следующему — то же самое, что показать ему
                // чужие счётчики (инвариант 8). Раньше auth-scope покрывал только счётчики.
                if (!ownerKnown) {
                    ownerKnown = true
                    owner = uid
                } else if (uid != owner) {
                    owner = uid
                    lastSuccessAtMs.value = 0L
                    lastError.value = null
                    unreadableChanges.value = 0
                }
                val counts =
                    if (uid.isNullOrBlank()) flowOf(OutboxCounts()) else store.observeCounts(uid)
                combine(counts, lastSuccessAtMs, lastError, unreadableChanges) { c, at, error, unreadable ->
                    SyncStatus(
                        lastSuccessAtMs = at,
                        counts = c,
                        lastError = error,
                        unreadableChanges = unreadable,
                    )
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

    override suspend fun recordUnreadableChanges(count: Int) {
        unreadableChanges.value = count
    }
}
