package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogCard
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.flow.StateFlow

/** Catalog reads remain independent; entry actions belong to the separately composed child. No runner authority. */
interface ArenaLessonCatalogComponent {
    val state: StateFlow<ArenaLessonCatalogUiState>
    val entry: ArenaEntryComponent? get() = null

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
    val session: ArenaSessionStamp? = null,
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
