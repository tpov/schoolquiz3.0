package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecyclePage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonLifecycleRepository

/** Страница размещений уроков автора (view `listOwnedLessonLifecycle`). */
class ListOwnedLifecycleUseCase(
    private val repository: LessonLifecycleRepository,
) {
    suspend operator fun invoke(
        ownerUid: String,
        afterLessonId: String? = null,
        pageSize: Int = ListCuratorLifecycleUseCase.DEFAULT_PAGE_SIZE,
    ): Result<LessonLifecyclePage> = repository.listOwned(ownerUid, afterLessonId, pageSize)
}
