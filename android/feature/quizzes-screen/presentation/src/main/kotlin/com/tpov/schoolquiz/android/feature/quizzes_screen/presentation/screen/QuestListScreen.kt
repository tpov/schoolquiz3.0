@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.BreadcrumbBar
import com.tpov.schoolquiz.android.core.designsystem.components.HierarchyDownloadStatus
import com.tpov.schoolquiz.android.core.designsystem.components.HierarchyItemCard
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirViolet
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuestListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuestListMode
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

private const val TAG = "QuestListScreen"
private const val PREVIEW_ALGEBRA_RATING = 2.5f
private const val PREVIEW_ALGEBRA_RATING_COUNT = 42
private const val PREVIEW_TRIGONOMETRY_RATING = 3.0f
private const val PREVIEW_TRIGONOMETRY_RATING_COUNT = 7

private data class PublicShelfAction(
    val shelf: String,
    val labelRes: Int,
)

private data class QuestListItemActions(
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
    val onDismissMenu: () -> Unit,
    val onDownloadClick: () -> Unit,
    val onShareClick: () -> Unit,
    val onSetShelfClick: (String) -> Unit,
)

private val publicShelfActions =
    listOf(
        PublicShelfAction("home", R.string.quizzes_shelf_show_home),
        PublicShelfAction("arena", R.string.quizzes_shelf_show_arena),
        PublicShelfAction("tournament", R.string.quizzes_shelf_show_qualification),
        PublicShelfAction("tournamentFinal", R.string.quizzes_shelf_show_world),
    )

@Composable
fun QuestListScreen(
    component: QuestListComponent,
    onSegmentClick: (Int) -> Unit,
    canManagePublicShelves: Boolean = false,
) {
    val uiState by component.uiState.subscribeAsState()

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
            is QuestListUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = LocalNoirAccent.current,
                    )
                }
            is QuestListUiState.Empty ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.quizzes_empty_quests),
                        modifier = Modifier.align(Alignment.Center),
                        style = NoirType.groupTitle,
                    )
                }
            is QuestListUiState.Loaded ->
                QuestListLoadedContent(
                    component = component,
                    state = state,
                    canManagePublicShelves = canManagePublicShelves,
                )
        }
    }
}

@Composable
private fun QuestListLoadedContent(
    component: QuestListComponent,
    state: QuestListUiState.Loaded,
    canManagePublicShelves: Boolean,
) {
    val lazyListState = rememberLazyListState()
    val context = LocalContext.current
    var expandedQuestId by remember { mutableStateOf<QuestId?>(null) }
    val isArena = component.mode == QuestListMode.Arena
    val isSelectionMode = component.selectionTargetShelf != null
    val useArenaRatingStyle = isArena || component.mode == QuestListMode.Archive

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = if (isArena && !isSelectionMode) 96.dp else 24.dp,
                ),
        ) {
            itemsIndexed(state.quests, key = { _, quest -> quest.id.value }) { index, quest ->
                QuestListItem(
                    quest = quest,
                    orderLabel = "${index + 1}.",
                    isMenuExpanded = expandedQuestId == quest.id,
                    useArenaRatingStyle = useArenaRatingStyle,
                    canManagePublicShelves = canManagePublicShelves,
                    actions =
                        QuestListItemActions(
                            onClick = { component.onQuestClick(quest) },
                            onLongClick = { expandedQuestId = quest.id },
                            onDismissMenu = { expandedQuestId = null },
                            onDownloadClick = { component.onQuestDownloadClick(quest) },
                            onShareClick = {
                                expandedQuestId = null
                                shareQuest(context, quest)
                            },
                            onSetShelfClick = { targetShelf ->
                                expandedQuestId = null
                                component.onSetShelfClick(quest, targetShelf)
                            },
                        ),
                )
            }
        }
        if (isArena && !isSelectionMode && state.quests.isNotEmpty()) {
            ArenaRandomQuestFab(onClick = component::onRandomQuestClick)
        }
        if (expandedQuestId != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("quest_menu_dismiss_layer")
                        .pointerInput(expandedQuestId) {
                            detectTapGestures { expandedQuestId = null }
                        },
            )
        }
    }
}

@Composable
private fun QuestListItem(
    quest: QuestDisplayItem,
    orderLabel: String,
    isMenuExpanded: Boolean,
    useArenaRatingStyle: Boolean,
    canManagePublicShelves: Boolean,
    actions: QuestListItemActions,
) {
    Box {
        HierarchyItemCard(
            title = quest.title,
            orderLabel = orderLabel,
            rating = quest.averageRating,
            ratingCount = quest.averageRatingCount,
            ratingTint = if (useArenaRatingStyle) NoirViolet else null,
            onClick = actions.onClick,
            onLongClick = actions.onLongClick,
            downloadStatus = quest.downloadStatus,
            onDownloadClick = actions.onDownloadClick,
        )
        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = actions.onDismissMenu,
            modifier = Modifier.testTag("quest_menu"),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.quizzes_menu_share)) },
                onClick = actions.onShareClick,
            )
            if (canManagePublicShelves) {
                publicShelfActions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(action.labelRes)) },
                        onClick = { actions.onSetShelfClick(action.shelf) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ArenaRandomQuestFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("arena_random_quest_fab"),
    ) {
        Icon(
            imageVector = Icons.Default.SportsEsports,
            contentDescription = stringResource(R.string.quizzes_cd_random_quest),
        )
    }
}

private fun shareQuest(
    context: Context,
    quest: QuestDisplayItem,
) {
    val appName =
        context.applicationInfo
            .loadLabel(context.packageManager).toString()
    val shareText = context.getString(R.string.quizzes_share_text, quest.title, appName)
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
                    override val breadcrumbs = listOf(BreadcrumbRoot.Catalogs)

                    override fun onQuestClick(quest: QuestDisplayItem) = Unit

                    override fun onQuestDownloadClick(quest: QuestDisplayItem) = Unit

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
                    override val breadcrumbs = listOf(BreadcrumbRoot.Catalogs)

                    override fun onQuestClick(quest: QuestDisplayItem) = Unit

                    override fun onQuestDownloadClick(quest: QuestDisplayItem) = Unit

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
                                            isDownloadable = true,
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
                    override val breadcrumbs = listOf(BreadcrumbRoot.Catalogs)

                    override fun onQuestClick(quest: QuestDisplayItem) = Unit

                    override fun onQuestDownloadClick(quest: QuestDisplayItem) = Unit

                    override fun onShareClick(quest: QuestDisplayItem) = Unit
                },
            onSegmentClick = {},
        )
    }
}

private val QuestDisplayItem.downloadStatus: HierarchyDownloadStatus
    get() =
        when {
            isDownloading -> HierarchyDownloadStatus.Downloading
            isDownloadComplete -> HierarchyDownloadStatus.Complete
            isDownloadable -> HierarchyDownloadStatus.Available
            else -> HierarchyDownloadStatus.Hidden
        }
