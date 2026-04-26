package com.tpov.schoolquiz.android.feature.quest.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.CatalogSpinner
import com.tpov.schoolquiz.android.core.designsystem.components.QuestCard
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.quests.isEmpty()) {
                Box(
                    modifier = Modifier
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
                    items(state.quests) { quest ->
                        QuestCard(
                            item = quest,
                            onClick = { component.onQuestClick(quest) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = component::onCreateQuestClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Создать квест")
        }
    }
}
