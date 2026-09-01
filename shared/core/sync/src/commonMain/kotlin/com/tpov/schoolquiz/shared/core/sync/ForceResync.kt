package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull

/**
 * Перечитать всё заново (AD-30).
 *
 * Сегодня этот путь есть только как хардкод для одного каталога, а разошедшиеся курсор и база
 * чинятся переустановкой приложения: `setCursor` монотонен, и курсор, ушедший вперёд локальных
 * данных, назад не возвращается никогда.
 *
 * **Лечит только сторону чтения.** Записи очереди и локальные эффекты, которые они уже применили
 * одной транзакцией с постановкой (AD-23), не трогаются: они верны локально, сервер о них ещё не
 * знает, и переигрывать их не нужно. Локальное состояние, разошедшееся из-за карантинной записи, —
 * не забота ресинка, а последствие карантина (AD-28).
 */
class ForceResync(
    private val syncStateRepo: SyncStateRepository,
    private val readSide: Syncable,
    private val gate: SyncGate = SyncGate(),
    /**
     * Куда сообщить исход.
     *
     * Без этого «Перечитать всё» — кнопка, после которой не происходит ничего видимого: успех
     * неотличим от неудачи, и игрок нажимает её второй раз.
     */
    private val status: SyncStatusRepository? = null,
    private val clock: () -> Long = { 0L },
) {
    /**
     * Обнуляет курсоры и запускает чтение с начала журналов.
     *
     * Порядок обязателен: сначала сброс, потом чтение. Обратный порядок оставил бы курсор от
     * прохода, который случился до сброса, и половина журнала снова оказалась бы пропущена.
     */
    suspend fun run(): Result<Unit> =
        // Через ворота: курсоры монотонны, поэтому проход, начавшийся до сброса, допишет своё
        // старое значение уже после него — и ресинк молча не сделает ничего.
        gate.withPass {
            syncStateRepo.resetAllCursors()
            val outcome = readSide.sync()
            outcome
                .onSuccess { status?.recordSuccess(clock()) }
                .onFailure { status?.recordFailure(it.syncErrorOrNull() ?: SyncError.Unknown(it), clock()) }
        }
}
