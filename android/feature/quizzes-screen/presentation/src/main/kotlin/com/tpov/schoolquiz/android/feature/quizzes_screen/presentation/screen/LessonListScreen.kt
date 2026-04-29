package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.BreadcrumbBar
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun LessonListScreen(
    component: LessonListComponent,
    onSegmentClick: (Int) -> Unit,
) {
    val uiState by component.uiState.subscribeAsState()
    val lazyListState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        BreadcrumbBar(titles = component.titles, onSegmentClick = onSegmentClick)
        when (val state = uiState) {
            is LessonListUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            is LessonListUiState.Empty ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = state.levelLabel,
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            is LessonListUiState.Loaded ->
                LazyColumn(state = lazyListState) {
                    items(state.items, key = { it.id }) { item ->
                        LessonItemCard(
                            item = item,
                            onClick = { component.onLessonClick(item) },
                            onHardCheckChanged = { component.onHardCheckToggled(item.id) },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun LessonListScreenLoadingPreview() {
    SchoolQuizTheme {
        LessonListScreen(
            component =
                object : LessonListComponent {
                    override val uiState: Value<LessonListUiState> = MutableValue(LessonListUiState.Loading)
                    override val titles = listOf("Математика", "Квест 1", "Секция 1", "Тема 1")

                    override fun onLessonClick(lesson: LessonItemUi) = Unit

                    override fun onHardCheckToggled(lessonId: String) = Unit
                },
            onSegmentClick = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun LessonListScreenLoadedPreview() {
    SchoolQuizTheme {
        LessonListScreen(
            component =
                object : LessonListComponent {
                    override val uiState: Value<LessonListUiState> =
                        MutableValue(
                            LessonListUiState.Loaded(
                                listOf(
                                    LessonItemUi(
                                        id = "l1",
                                        title = "Урок 1 — Введение",
                                        orderLabel = "1.",
                                        bestStarsRawTenths = 20,
                                    ),
                                    LessonItemUi(
                                        id = "l2",
                                        title = "Урок 2 — Практика",
                                        orderLabel = "2.",
                                        bestStarsRawTenths = 0,
                                        hardUnlocked = true,
                                        isHardChecked = false,
                                    ),
                                ),
                            ),
                        )
                    override val titles = listOf("Математика", "Квест 1", "Секция 1", "Тема 1")

                    override fun onLessonClick(lesson: LessonItemUi) = Unit

                    override fun onHardCheckToggled(lessonId: String) = Unit
                },
            onSegmentClick = {},
        )
    }
}
