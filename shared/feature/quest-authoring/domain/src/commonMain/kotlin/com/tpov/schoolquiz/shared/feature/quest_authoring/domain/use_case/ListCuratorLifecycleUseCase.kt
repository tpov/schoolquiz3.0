package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecyclePage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonLifecycleRepository

/** Страница размещений для куратора (view `listCuratorLessonLifecycle`). */
class ListCuratorLifecycleUseCase(
    private val repository: LessonLifecycleRepository,
) {
    suspend operator fun invoke(
        ownerUid: String,
        afterLessonId: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Result<LessonLifecyclePage> = repository.listCurator(ownerUid, afterLessonId, pageSize)

    companion object {
        /** Серверный потолок страницы — 20 (LIMITS views); клиент просит сразу его. */
        const val DEFAULT_PAGE_SIZE = 20
    }
}
