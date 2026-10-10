package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxItem

/**
 * Экран держит доменные проекции inbox как есть — значения, а не поведение (AD-14). Каждая
 * строка несёт свой круг читателей: приватный отчёт помечен для автора, публичный комментарий —
 * для всех, и слова на экране за ними закреплены.
 */
data class InboxUiState(
    val isLoading: Boolean = true,
    val errorMessage: UiMessage? = null,
    val entries: List<InboxItem> = emptyList(),
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
)
