package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.navigation.QuizzesNavigator
import kotlinx.coroutines.flow.StateFlow

interface QuizzesComponent : QuizzesNavigator {
    val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>>

    /**
     * Name of the catalog the user is currently exploring (or null when on Idle).
     * Derived from the active drill-down config so presentation does not need to
     * know the breadcrumb structure.
     */
    val currentCatalogName: StateFlow<String?>

    /**
     * Floating-icon set for the currently active catalog. Resolved entirely from the
     * Firestore-driven `catalog.iconNames` list, mapped through the design-system
     * whitelist registry. Returns an empty list when on Idle, when the catalog is
     * not loaded yet, or when no icon name from the list is present in the registry.
     * Presentation should treat empty as "no floating background".
     */
    val currentCatalogIcons: StateFlow<List<ImageVector>>

    /**
     * Breadcrumb segment tap.
     * @param uiLevel 0-based index of the user-visible segment (catalog=0, quest=1, ...).
     * Internally calls navigation.popTo(uiLevel + 1) — offset +1 for Idle anchor at stack[0].
     */
    fun popToLevel(uiLevel: Int)

    fun popCurrentChild()

    /**
     * Replace the active lesson runner with one for [lessonId] (design decision F3 —
     * "Next lesson →"). Mode, session mode and breadcrumb titles are carried over from the
     * active runner config; a no-op when the runner is not on top.
     */
    fun openLessonRunner(lessonId: String)
}
