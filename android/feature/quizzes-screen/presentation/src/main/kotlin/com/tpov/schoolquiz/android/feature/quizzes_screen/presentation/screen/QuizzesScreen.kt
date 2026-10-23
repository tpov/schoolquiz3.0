package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.FloatingIconsLayer
import com.tpov.schoolquiz.android.core.designsystem.noir.noirScreenGround
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.PrivateQuizRunnerScreen

import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaRunNavigation
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun QuizzesScreen(
    component: QuizzesComponent,
    canManagePublicShelves: Boolean = false,
) {
    val stack by component.childStack.subscribeAsState()

    val active = stack.active.instance
    val iconSet by component.currentCatalogIcons.collectAsState()
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    enabled = false,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {},
    ) {
        if (active !is QuizzesChild.Idle && active !is QuizzesChild.LessonRunner &&
            active !is QuizzesChild.PrivateQuizRunner
        ) {
            // PrivateQuizRunner — ранер: фон экранов не кладётся под него (как у LessonRunner).
            Box(
                Modifier
                    .fillMaxSize()
                    .noirScreenGround(),
            ) {}
            FloatingIconsLayer(
                modifier = Modifier.fillMaxSize(),
                icons = iconSet,
            )
        }
        when (active) {
            is QuizzesChild.Idle -> Unit
            is QuizzesChild.ArenaLessons -> {
                val arenaState by active.component.state.collectAsState()
                val entry = active.component.entry
                val entryState = entry?.state?.collectAsState()?.value
                val entryCallbacks =
                    ArenaEntryCallbacks(
                        onEvent = { entry?.onEvent(it) },
                        onOpenRun =
                            (component as? ArenaRunNavigation)
                                ?.let { it::openArenaRunner },
                    )
                ArenaLessonCatalogScreen(
                    state = arenaState,
                    onRefresh = active.component::onRefresh,
                    onLoadMore = active.component::onLoadMore,
                    onBack = component::popCurrentChild,
                    entryState = entryState,
                    entryCallbacks = entryCallbacks,
                )
            }
            is QuizzesChild.PublicQuestCatalogPicker ->
                PublicQuestCatalogPickerScreen(
                    component = active.component,
                    onSegmentClick = component::popToLevel,
                )
            is QuizzesChild.QuestList ->
                QuestListScreen(
                    component = active.component,
                    onSegmentClick = component::popToLevel,
                    canManagePublicShelves = canManagePublicShelves,
                )
            is QuizzesChild.SectionList -> SectionListScreen(active.component, onSegmentClick = component::popToLevel)
            is QuizzesChild.ThemeList -> ThemeListScreen(active.component, onSegmentClick = component::popToLevel)
            is QuizzesChild.LessonList -> LessonListScreen(active.component, onSegmentClick = component::popToLevel)
            is QuizzesChild.LessonRunner ->
                LessonRunnerScreen(
                    component = active.component,
                    onNavigateBack = { component.popCurrentChild() },
                    onOpenLesson = component::openLessonRunner,
                )
            is QuizzesChild.PrivateQuizRunner ->
                PrivateQuizRunnerScreen(
                    component = active.component,
                    modifier = Modifier,
                )
            is QuizzesChild.LessonEarnings ->
                LessonEarningsScreen(
                    component = active.component,
                    modifier = Modifier,
                )
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun QuizzesScreenPreview() {
    SchoolQuizTheme {
        // Preview requires a real component — shown via child screens instead
    }
}
