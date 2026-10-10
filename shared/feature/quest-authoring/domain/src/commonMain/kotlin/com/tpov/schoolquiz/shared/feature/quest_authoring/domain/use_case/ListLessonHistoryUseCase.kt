package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonLifecycleRepository

/** История кураторских решений по одному уроку (view `listLessonLifecycleHistory`). */
class ListLessonHistoryUseCase(
    private val repository: LessonLifecycleRepository,
) {
    suspend operator fun invoke(
        ownerUid: String,
        lessonId: String,
        cursor: String? = null,
        pageSize: Int = ListCuratorLifecycleUseCase.DEFAULT_PAGE_SIZE,
    ): Result<LessonHistoryPage> = repository.listHistory(ownerUid, lessonId, cursor, pageSize)
}
