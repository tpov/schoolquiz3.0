package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogCard
import kotlinx.coroutines.flow.StateFlow

/** Read-only actions only. There is intentionally no lesson click, prepare or start callback. */
interface ArenaLessonCatalogComponent {
    val state: StateFlow<ArenaLessonCatalogUiState>

    fun onRefresh()

    fun onLoadMore()
}

/** Resolving this factory must not resolve a runner or make an HTTP request. */
fun interface ArenaLessonCatalogComponentFactory {
    fun create(componentContext: ComponentContext): ArenaLessonCatalogComponent
}

data class ArenaLessonCatalogUiState(
    val lessons: List<ArenaLessonCatalogCard> = emptyList(),
    val totalLessons: Int? = null,
    val isLoading: Boolean = false,
    val canLoadMore: Boolean = false,
    val sessionAvailable: Boolean = false,
    val notice: ArenaCatalogNotice? = ArenaCatalogNotice.NOT_LOADED,
)

enum class ArenaCatalogNotice {
    NOT_LOADED,
    ACCOUNT_UNAVAILABLE,
    ACCOUNT_CHANGED,
    CATALOG_CHANGED,
    UNAVAILABLE,
    TOO_LARGE,
    INVALID_CATALOG,
}
