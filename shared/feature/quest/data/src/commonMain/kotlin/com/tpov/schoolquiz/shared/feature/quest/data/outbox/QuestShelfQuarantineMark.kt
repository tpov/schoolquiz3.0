package com.tpov.schoolquiz.shared.feature.quest.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.QuarantineListener
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

/**
 * Что делает фича quest, когда перенос квеста на полку ушёл в карантин (AD-28).
 *
 * Откатывать нечего: полка серверно-защищена и локально не менялась (AD-25), локальной половины у
 * записи не было. Последствие карантина здесь одно — явная пометка на карточке квеста, и её
 * источник — сама запись очереди: запрос по `entity_ref` (история 4.4) отвечает «карантин», а
 * счётчик карантинных в блоке состояния (история 4.5) растёт от той же записи. Вторую таблицу с
 * пометками фича не заводит — она разошлась бы с очередью.
 *
 * Обработчик всё же объявлен, а не заменён молчанием: «нечего откатывать» и «забыли обработчик»
 * не должны выглядеть одинаково. Он проверяет, что ссылка действительно наша, и отдаёт квест с
 * причиной наружу — в журнал.
 */
class QuestShelfQuarantineMark(
    private val onMarked: (questId: QuestId, reason: String?) -> Unit = { _, _ -> },
) : QuarantineListener {
    override suspend fun onQuarantined(record: OutboxRecord) {
        if (record.operation != QuestShelfOutbox.OPERATION) return
        val questId = QuestShelfOutbox.questIdOf(record.entityRef) ?: return
        onMarked(questId, record.lastError)
    }
}
