package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.BreadcrumbBar
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NOIR_WASH_MIDPOINT_LATE
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirWashQuizzes
import com.tpov.schoolquiz.android.core.designsystem.noir.noirScreenWash
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyLevel
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

    Column(
        modifier = Modifier.fillMaxSize().noirScreenWash(NoirWashQuizzes, midpoint = NOIR_WASH_MIDPOINT_LATE),
    ) {
        BreadcrumbBar(
            titles = breadcrumbTitles(component.breadcrumbs),
            onSegmentClick = onSegmentClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when (val state = uiState) {
            is LessonListUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = LocalNoirAccent.current,
                    )
                }
            is LessonListUiState.Empty ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text =
                            stringResource(
                                when (state.level) {
                                    HierarchyLevel.LESSONS -> R.string.quizzes_empty_lessons
                                    HierarchyLevel.SECTIONS -> R.string.quizzes_empty_sections
                                    HierarchyLevel.THEMES -> R.string.quizzes_empty_themes
                                },
                            ),
                        modifier = Modifier.align(Alignment.Center),
                        style = NoirType.groupTitle,
                    )
                }
            is LessonListUiState.Loaded ->
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        LessonItemCard(
                            item = item,
                            onClick = { component.onLessonClick(item) },
                            onHardCheckChanged = { component.onHardCheckToggled(item.id) },
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
                    override val breadcrumbs =
                        listOf(
                            BreadcrumbRoot.Catalogs,
                            BreadcrumbRoot.Dynamic("Квест 1"),
                            BreadcrumbRoot.Dynamic("Секция 1"),
                            BreadcrumbRoot.Dynamic("Тема 1"),
                        )

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
                                        averageRating = 2.0f,
                                        ratingCount = 12,
                                        bestStarsRawTenths = 20,
                                        isDownloaded = true,
                                    ),
                                    LessonItemUi(
                                        id = "l2",
                                        title = "Урок 2 — Практика",
                                        orderLabel = "2.",
                                        averageRating = null,
                                        bestStarsRawTenths = 0,
                                        hardUnlocked = true,
                                        isHardChecked = false,
                                        isDownloaded = false,
                                    ),
                                ),
                            ),
                        )
                    override val breadcrumbs =
                        listOf(
                            BreadcrumbRoot.Catalogs,
                            BreadcrumbRoot.Dynamic("Квест 1"),
                            BreadcrumbRoot.Dynamic("Секция 1"),
                            BreadcrumbRoot.Dynamic("Тема 1"),
                        )

                    override fun onLessonClick(lesson: LessonItemUi) = Unit

                    override fun onHardCheckToggled(lessonId: String) = Unit
                },
            onSegmentClick = {},
        )
    }
}
