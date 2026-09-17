package com.tpov.schoolquiz.shared.feature.quest.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestShelfChange
import com.tpov.schoolquiz.shared.feature.quest.domain.provider.QuestShelfChangeIdProvider
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository

/**
 * Переносит публичный квест на другую полку — постановкой намерения в очередь, а не вызовом сервера.
 *
 * Перенос откладывается по AD-3: его исход детерминированно выводится из того, что у клиента уже
 * есть, и ничего сверх этого сервер не спрашивает. Локальная половина пуста (AD-25): полка —
 * серверно-защищённое поле, и локальная строка квеста здесь не трогается. Новая полка приедет
 * журналом изменений, когда сервер применит перенос; до этого ни один снимок строки квест на новой
 * полке не покажет.
 *
 * Ключ намерения выдаётся здесь, в момент нажатия (AD-2), и живёт дальше вместе с записью очереди.
 */
class SetPublicQuestShelfUseCase(
    private val quests: QuestRepository,
    private val idProvider: QuestShelfChangeIdProvider = QuestShelfChangeIdProvider.Random,
) {
    suspend fun execute(
        ownerUid: String,
        questId: QuestId,
        targetShelf: String,
    ): Result<Unit> =
        runCatching {
            require(targetShelf in Quest.VALID_SHELVES) { "Unsupported targetShelf: $targetShelf" }
            QuestShelfChange(
                id = idProvider.nextId(),
                ownerUid = ownerUid,
                questId = questId,
                targetShelf = targetShelf,
            )
        }.fold(
            onSuccess = { quests.queueShelfChange(it) },
            onFailure = { Result.failure(it) },
        )
}
