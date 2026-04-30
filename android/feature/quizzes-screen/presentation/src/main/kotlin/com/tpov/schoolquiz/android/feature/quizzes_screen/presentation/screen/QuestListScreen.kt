package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.BreadcrumbBar
import com.tpov.schoolquiz.android.core.designsystem.components.HierarchyItemCard
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuestListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

private const val TAG = "QuestListScreen"
private const val PREVIEW_ALGEBRA_RATING = 2.5f
private const val PREVIEW_ALGEBRA_RATING_COUNT = 42
private const val PREVIEW_TRIGONOMETRY_RATING = 3.0f
private const val PREVIEW_TRIGONOMETRY_RATING_COUNT = 7

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun QuestListScreen(
    component: QuestListComponent,
    onSegmentClick: (Int) -> Unit,
) {
    val uiState by component.uiState.subscribeAsState()
    val lazyListState = rememberLazyListState()
    val context = LocalContext.current
    var expandedQuestId by remember { mutableStateOf<QuestId?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        BreadcrumbBar(
            titles = component.titles,
            onSegmentClick = onSegmentClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when (val state = uiState) {
            is QuestListUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            is QuestListUiState.Empty ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Нет квестов",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            is QuestListUiState.Loaded ->
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(state.quests, key = { _, quest -> quest.id.value }) { index, quest ->
                        Box {
                            HierarchyItemCard(
                                title = quest.title,
                                orderLabel = "${index + 1}.",
                                rating = quest.averageRating,
                                ratingCount = quest.averageRatingCount,
                                onClick = { component.onQuestClick(quest) },
                                onLongClick = { expandedQuestId = quest.id },
                            )
                            DropdownMenu(
                                expanded = expandedQuestId == quest.id,
                                onDismissRequest = { expandedQuestId = null },
                                modifier = Modifier.testTag("quest_menu"),
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Поделиться") },
                                    onClick = {
                                        expandedQuestId = null
                                        val appName =
                                            context.applicationInfo
                                                .loadLabel(context.packageManager).toString()
                                        val shareText = "Квест «${quest.title}» — $appName"
                                        val intent =
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                        try {
                                            context.startActivity(Intent.createChooser(intent, null))
                                        } catch (e: ActivityNotFoundException) {
                                            Log.w(TAG, "Share unavailable", e)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun QuestListScreenLoadingPreview() {
    SchoolQuizTheme {
        QuestListScreen(
            component =
                object : QuestListComponent {
                    override val uiState: Value<QuestListUiState> = MutableValue(QuestListUiState.Loading)
                    override val titles = listOf("Математика")

                    override fun onQuestClick(quest: QuestDisplayItem) = Unit

                    override fun onShareClick(quest: QuestDisplayItem) = Unit
                },
            onSegmentClick = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun QuestListScreenEmptyPreview() {
    SchoolQuizTheme {
        QuestListScreen(
            component =
                object : QuestListComponent {
                    override val uiState: Value<QuestListUiState> = MutableValue(QuestListUiState.Empty)
                    override val titles = listOf("Математика")

                    override fun onQuestClick(quest: QuestDisplayItem) = Unit

                    override fun onShareClick(quest: QuestDisplayItem) = Unit
                },
            onSegmentClick = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun QuestListScreenLoadedPreview() {
    val catalogId = CatalogId("cat-1")
    SchoolQuizTheme {
        QuestListScreen(
            component =
                object : QuestListComponent {
                    override val uiState: Value<QuestListUiState> =
                        MutableValue(
                            QuestListUiState.Loaded(
                                quests =
                                    listOf(
                                        QuestDisplayItem(
                                            QuestId("1"),
                                            catalogId,
                                            "Алгебра — основы",
                                            null,
                                            PREVIEW_ALGEBRA_RATING,
                                            PREVIEW_ALGEBRA_RATING_COUNT,
                                        ),
                                        QuestDisplayItem(QuestId("2"), catalogId, "Геометрия", null, null, 0),
                                        QuestDisplayItem(
                                            QuestId("3"),
                                            catalogId,
                                            "Тригонометрия",
                                            null,
                                            PREVIEW_TRIGONOMETRY_RATING,
                                            PREVIEW_TRIGONOMETRY_RATING_COUNT,
                                        ),
                                    ),
                            ),
                        )
                    override val titles = listOf("Математика")

                    override fun onQuestClick(quest: QuestDisplayItem) = Unit

                    override fun onShareClick(quest: QuestDisplayItem) = Unit
                },
            onSegmentClick = {},
        )
    }
}
