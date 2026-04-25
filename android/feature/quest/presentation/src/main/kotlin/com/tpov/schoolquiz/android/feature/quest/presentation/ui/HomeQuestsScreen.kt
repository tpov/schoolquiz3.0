package com.tpov.schoolquiz.android.feature.quest.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.CatalogGrid
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent

/**
 * Home (catalog grid) screen.
 *
 * Shows CatalogGrid (2-column grid with titleMedium bold typography, 16dp corners, 12dp gap).
 * Archived catalogs excluded at DAO level (WHERE archived=0).
 *
 * Spec: AC#21-22; DFD 2 (02-behavior.md)
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun HomeQuestsScreen(
    component: HomeQuestsComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (state.catalogs.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Нет каталогов",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Синхронизируйте данные через меню",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    } else {
        CatalogGrid(
            items = state.catalogs,
            onCatalogClick = component::onCatalogClick,
            modifier = modifier.fillMaxSize(),
        )
    }
}
