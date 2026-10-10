package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxAnchor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.InboxRepository
import kotlinx.coroutines.CancellationException

/**
 * Чтение общего inbox (AD-20). Право проверяет серверная вью; не-автор получает пустую страницу,
 * а не отказ — как и в остальных авторских поверхностях эпика.
 */
class ListInboxItemsUseCase(
    private val repository: InboxRepository,
) {
    suspend operator fun invoke(
        pageSize: Int = 20,
        after: InboxAnchor? = null,
    ): Result<InboxPage> =
        try {
            require(pageSize in 1..20) { "pageSize must be within the server page bounds" }
            repository.listInboxItems(pageSize, after)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
}
