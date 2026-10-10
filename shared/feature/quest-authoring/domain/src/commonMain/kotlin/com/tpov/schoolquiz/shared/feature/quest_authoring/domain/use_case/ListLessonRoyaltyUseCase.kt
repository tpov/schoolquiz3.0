package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonRoyaltyPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonLifecycleRepository

/** Авторские роялти арены (view `listMyArenaRoyalty`). */
class ListLessonRoyaltyUseCase(
    private val repository: LessonLifecycleRepository,
) {
    suspend operator fun invoke(
        ownerUid: String,
        cursor: String? = null,
        pageSize: Int = ListCuratorLifecycleUseCase.DEFAULT_PAGE_SIZE,
    ): Result<LessonRoyaltyPage> = repository.listRoyalty(ownerUid, cursor, pageSize)
}
