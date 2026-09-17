package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository

/**
 * Открыть выбранный черновик на редактирование.
 *
 * Редактор всегда показывает активный черновик, поэтому «открыть тот, на который нажали» — это
 * смена активного. Прежде выбор в списке терялся: обработчик нажатия принимал элемент списка и
 * никак его не использовал, просто уводя на экран создания, — и автор с двумя черновиками
 * попадал не в тот, который выбрал, без единого признака подмены.
 */
class OpenQuestDraftUseCase(
    private val repository: QuestAuthoringRepository,
) {
    suspend operator fun invoke(
        ownerUid: String,
        draftId: QuestDraftId,
    ): Result<Unit> {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        return repository.activateDraft(ownerUid = ownerUid, draftId = draftId)
    }
}
