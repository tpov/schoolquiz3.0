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
import com.tpov.schoolquiz.android.core.designsystem.components.HierarchyItemCard
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.SectionListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyLevel
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun SectionListScreen(
    component: SectionListComponent,
    onSegmentClick: (Int) -> Unit,
) {
    val uiState by component.uiState.subscribeAsState()
    val lazyListState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        BreadcrumbBar(
            titles = breadcrumbTitles(component.breadcrumbs),
            onSegmentClick = onSegmentClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when (val state = uiState) {
            is HierarchyListUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = LocalNoirAccent.current,
                    )
                }
            is HierarchyListUiState.Empty ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text =
                            stringResource(
                                when (state.level) {
                                    HierarchyLevel.SECTIONS -> R.string.quizzes_empty_sections
                                    HierarchyLevel.THEMES -> R.string.quizzes_empty_themes
                                    HierarchyLevel.LESSONS -> R.string.quizzes_empty_lessons
                                },
                            ),
                        modifier = Modifier.align(Alignment.Center),
                        style = NoirType.groupTitle,
                    )
                }
            is HierarchyListUiState.Loaded ->
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        HierarchyItemCard(
                            title = item.title,
                            orderLabel = item.orderLabel,
                            subtitleCount = item.subtitleCount,
                            onClick = { component.onSectionClick(item) },
                        )
                    }
                }
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun SectionListScreenLoadingPreview() {
    SchoolQuizTheme {
        SectionListScreen(
            component =
                object : SectionListComponent {
                    override val uiState: Value<HierarchyListUiState> = MutableValue(HierarchyListUiState.Loading)
                    override val breadcrumbs =
                        listOf(BreadcrumbRoot.Catalogs, BreadcrumbRoot.Dynamic("Квест 1"))

                    override fun onSectionClick(section: HierarchyItemUi) = Unit
                },
            onSegmentClick = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun SectionListScreenLoadedPreview() {
    SchoolQuizTheme {
        SectionListScreen(
            component =
                object : SectionListComponent {
                    override val uiState: Value<HierarchyListUiState> =
                        MutableValue(
                            HierarchyListUiState.Loaded(
                                listOf(
                                    HierarchyItemUi(id = "1", title = "Секция 1 — Введение", orderLabel = "1."),
                                    HierarchyItemUi(
                                        id = "2",
                                        title = "Секция 2 — Основы",
                                        orderLabel = "2.",
                                        subtitleCount = "5 тем",
                                    ),
                                ),
                            ),
                        )
                    override val breadcrumbs =
                        listOf(BreadcrumbRoot.Catalogs, BreadcrumbRoot.Dynamic("Квест 1"))

                    override fun onSectionClick(section: HierarchyItemUi) = Unit
                },
            onSegmentClick = {},
        )
    }
}
