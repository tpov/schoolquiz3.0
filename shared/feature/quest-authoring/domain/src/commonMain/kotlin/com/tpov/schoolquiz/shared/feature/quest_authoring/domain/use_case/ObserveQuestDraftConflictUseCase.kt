package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftConflict
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.flow.Flow

/**
 * Расходится ли этот черновик с сервером прямо сейчас (AD-24, AD-14).
 *
 * Отдаёт `null`, когда расхождения нет, — экран по нему и решает, показывать ли действие
 * «отправить заново». Ждать ответа не надо: поток живёт столько же, сколько экран.
 */
class ObserveQuestDraftConflictUseCase(
    private val repository: QuestAuthoringRepository,
) {
    operator fun invoke(
        ownerUid: String,
        draftId: QuestDraftId,
    ): Flow<QuestDraftConflict?> = repository.observeDraftConflict(ownerUid, draftId)
}
