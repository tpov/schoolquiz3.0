package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.tpov.schoolquiz.android.core.designsystem.components.BreadcrumbBar
import com.tpov.schoolquiz.android.core.designsystem.components.CatalogGrid
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.PublicQuestCatalogPickerComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.PublicQuestCatalogPickerUiState

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun PublicQuestCatalogPickerScreen(
    component: PublicQuestCatalogPickerComponent,
    onSegmentClick: (Int) -> Unit,
) {
    val state by component.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        BreadcrumbBar(
            titles = component.titles,
            onSegmentClick = onSegmentClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when (val current = state) {
            PublicQuestCatalogPickerUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            PublicQuestCatalogPickerUiState.Empty ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Нет каталогов",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            is PublicQuestCatalogPickerUiState.Loaded ->
                CatalogGrid(
                    items = current.catalogs,
                    onCatalogClick = component::onCatalogClick,
                    modifier = Modifier.fillMaxSize(),
                )
        }
    }
}
