package com.tpov.schoolquiz.android.feature.quest.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.BrandCard
import com.tpov.schoolquiz.android.core.designsystem.components.BrandSquareIconButton
import com.tpov.schoolquiz.android.core.designsystem.components.CatalogSpinner
import com.tpov.schoolquiz.android.core.designsystem.components.QuestCard
import com.tpov.schoolquiz.android.feature.quest.presentation.DraftQuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent

/**
 * "Мои квесты" screen.
 *
 * Layout:
 *   - CatalogSpinner (category filter)
 *   - LazyColumn of QuestCards
 *   - Empty state with arrow-to-FAB hint when no quests
 *   - FAB: create quest (navigates to OpenQuestCreate)
 *
 * isGuest=true → same empty state as 0 quests (no login CTA per spec).
 *
 * Spec: AC#23-29; DFD 3 (02-behavior.md)
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun MyQuestsScreen(
    component: MyQuestsComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CatalogSpinner(
                items = state.catalogs,
                selectedId = state.selectedCatalogId,
                onSelectionChanged = component::onCatalogSelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.quests.isEmpty() && state.drafts.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Нет квестов",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Нажмите + чтобы создать первый квест",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.drafts) { draft ->
                        DraftQuestCard(
                            item = draft,
                            onClick = { component.onDraftClick(draft) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    items(state.quests) { quest ->
                        QuestCard(
                            item = quest,
                            onClick = { component.onQuestClick(quest) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        BrandSquareIconButton(
            icon = Icons.Default.Add,
            contentDescription = "Создать квест",
            onClick = component::onCreateQuestClick,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DraftQuestCard(
    item: DraftQuestDisplayItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BrandCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Черновик",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = "Вопросов: ${item.questionCount}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
