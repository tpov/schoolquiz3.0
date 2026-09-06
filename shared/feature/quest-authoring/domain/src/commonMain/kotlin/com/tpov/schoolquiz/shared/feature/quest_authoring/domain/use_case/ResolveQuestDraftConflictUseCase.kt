package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository

/**
 * Решение автора по расхождению: принять серверную версию и отправить работу заново (AD-24).
 *
 * Повтором прежней заявки это быть не может: сервер уже видел её ключ и отверг, а повтор под тем
 * же ключом не применит ничего (AD-2). Поэтому решение порождает новую мутацию, а прежняя запись
 * снимается — обе половины одной транзакцией.
 */
class ResolveQuestDraftConflictUseCase(
    private val repository: QuestAuthoringRepository,
) {
    suspend operator fun invoke(
        ownerUid: String,
        draftId: QuestDraftId,
    ): Result<Unit> = repository.resolveDraftConflict(ownerUid, draftId)
}
