package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.BreadcrumbBar
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonPlaceholderComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonPlaceholderUiState

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun LessonPlaceholderScreen(
    component: LessonPlaceholderComponent,
    onSegmentClick: (Int) -> Unit,
) {
    val uiState = component.uiState

    Column(modifier = Modifier.fillMaxSize()) {
        BreadcrumbBar(titles = uiState.titles, onSegmentClick = onSegmentClick)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = uiState.lessonTitle,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Прохождение урока будет добавлено позже",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun LessonPlaceholderScreenPreview() {
    SchoolQuizTheme {
        LessonPlaceholderScreen(
            component = object : LessonPlaceholderComponent {
                override val uiState = LessonPlaceholderUiState(
                    lessonTitle = "Урок: Дроби",
                    titles = listOf("Математика", "Квест 1", "Секция 2", "Тема 3", "Урок: Дроби"),
                )
            },
            onSegmentClick = {},
        )
    }
}
