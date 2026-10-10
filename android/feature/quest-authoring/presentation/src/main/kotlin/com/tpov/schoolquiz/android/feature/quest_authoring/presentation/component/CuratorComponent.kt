package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.CuratorUiState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonShelf
import kotlinx.coroutines.flow.StateFlow

/**
 * Поверхность куратора: размещения всех уроков (view `listCuratorLessonLifecycle`), история
 * решений выбранного урока и переход по полкам (`setLessonPlacement`).
 *
 * Разрешения проверяет сервер (TZ06B): не-куратор видит отказ чтения как [CuratorUiState.permissionDenied].
 */
interface CuratorComponent {
    val state: StateFlow<CuratorUiState>

    fun onRefreshClick()

    fun onLoadMoreClick()

    fun onLessonSelected(lessonId: String)

    fun onReasonChanged(value: String)

    fun onTransitionClick(target: LessonShelf)
}
