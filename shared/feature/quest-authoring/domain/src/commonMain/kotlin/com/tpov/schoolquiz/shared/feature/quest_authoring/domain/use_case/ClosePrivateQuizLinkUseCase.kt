package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.CancellationException

/**
 * Ручное закрытие приватной ссылки — решение автора (AD-29/UX-16, история 8.3).
 *
 * Закрытие окончательно: re-enable (как и автопродление/перевыпуск) решением не назначен.
 * Уже начатая попытка не при чём — отказывают только НОВЫЕ старты, это слово сервера.
 * Намерение уходит в общую очередь (AD-6) и переживает офлайн.
 */
class ClosePrivateQuizLinkUseCase(
    private val repository: QuestAuthoringRepository,
) {
    suspend operator fun invoke(
        ownerUid: String,
        catalogId: String,
        questId: String,
    ): Result<Unit> {
        return try {
            require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
            require(catalogId.isNotBlank()) { "catalogId must not be blank" }
            require(questId.isNotBlank()) { "questId must not be blank" }
            repository.queueClosePrivateQuizLink(ownerUid, catalogId, questId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }
}
