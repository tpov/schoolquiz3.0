package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.AuthorLifecycleUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Авторская половина TZ06B: статусы собственных уроков (view `listOwnedLessonLifecycle`) и
 * авторские роялти (view `listMyArenaRoyalty`). Только чтение — решает сервер, автор видит.
 */
interface AuthorLifecycleComponent {
    val state: StateFlow<AuthorLifecycleUiState>

    fun onRefreshClick()

    fun onLoadMoreClick()

    fun onLessonSelected(lessonId: String)
}
