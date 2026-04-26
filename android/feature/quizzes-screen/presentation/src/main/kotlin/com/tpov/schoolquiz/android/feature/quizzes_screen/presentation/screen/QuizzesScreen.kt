package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun QuizzesScreen(component: QuizzesComponent) {
    val stack by component.childStack.subscribeAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                enabled = false,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {},
    ) {
        when (val active = stack.active.instance) {
            is QuizzesChild.Idle -> Unit
            is QuizzesChild.QuestList -> QuestListScreen(active.component, onSegmentClick = component::popToLevel)
            is QuizzesChild.SectionList -> SectionListScreen(active.component, onSegmentClick = component::popToLevel)
            is QuizzesChild.ThemeList -> ThemeListScreen(active.component, onSegmentClick = component::popToLevel)
            is QuizzesChild.LessonList -> LessonListScreen(active.component, onSegmentClick = component::popToLevel)
            is QuizzesChild.LessonPlaceholder -> LessonPlaceholderScreen(active.component, onSegmentClick = component::popToLevel)
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
