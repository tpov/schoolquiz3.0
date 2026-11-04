package com.tpov.schoolquiz.shared.feature.internet.social.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * Наблюдение очереди поверхностью опросов (ретро эпика 8, находка 7, UX-O7 §4).
 *
 * Замки серии, бейдж «не отправлено» и показ отказа собираются из [OutboxStore.observeEntity] —
 * факта очереди, а не из памяти компонента: отказ мутации или смерть процесса переживает
 * перезапуск, локальный флаг — нет. Ссылку на сущность собирает фича (AD-7) —
 * [PollOutboxWriters.seriesRef]; ядро сравнивает её целиком и в части не заглядывает.
 *
 * Список наблюдается сочетанием потоков по текущему набору серий (≤ pageSize): отдельного
 * запроса «все ряды моих серий» у ядра нет, и знать про форму ссылки оно не должно (AD-7).
 * Ответ по каждой ссылке всегда есть: [OutboxEntitySync.None] — «неотправленного нет», а не
 * пустота (AD-8).
 */
interface PollOutboxRepository {
    /** Состояние очереди по каждой из названных серий — в порядке [seriesIds]. */
    fun observe(
        ownerUid: String,
        seriesIds: List<String>,
    ): Flow<List<OutboxEntitySync>>
}

class DefaultPollOutboxRepository(
    private val outbox: OutboxStore,
) : PollOutboxRepository {
    override fun observe(
        ownerUid: String,
        seriesIds: List<String>,
    ): Flow<List<OutboxEntitySync>> =
        if (seriesIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                seriesIds.map { seriesId -> outbox.observeEntity(ownerUid, PollOutboxWriters.seriesRef(seriesId)) },
            ) { syncs -> syncs.toList() }
        }
}
