package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.InboxUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Поверхность общего inbox (AD-20): разрешённые проекции того, что адресовано автору.
 *
 * Право проверяет серверная вью; не-автор видит пустой список, а не отказ. Продолжение списка —
 * по якорю последней строки; перечитывание по «Обновить» всегда с начала.
 */
interface InboxComponent {
    val state: StateFlow<InboxUiState>

    fun onRefreshClick()

    fun onLoadMoreClick()
}
