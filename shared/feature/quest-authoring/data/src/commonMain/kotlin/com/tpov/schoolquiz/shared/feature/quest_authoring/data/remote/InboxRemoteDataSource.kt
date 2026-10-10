package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxAnchor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxPage

/**
 * Транспорт общего inbox (AD-20): один читающий view-коллбл.
 *
 * Владелец сессии выводится сервером из Firebase Auth — в полезной нагрузке его нет. Ответ
 * приводится к доменной проекции строгим парсером; расхождение конверта — отказ чтения.
 */
interface InboxRemoteDataSource {
    suspend fun listInboxItems(
        pageSize: Int,
        after: InboxAnchor?,
    ): InboxPage
}
