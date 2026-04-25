package com.tpov.schoolquiz.android.feature.app_shell.presentation.fake

import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsUiState
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object StubMyQuestsComponent : MyQuestsComponent {
    override val state: StateFlow<MyQuestsUiState> = MutableStateFlow(MyQuestsUiState())
    override fun onCatalogSelected(id: CatalogId?) = Unit
    override fun onCreateQuestClick() = Unit
}

object StubHomeQuestsComponent : HomeQuestsComponent {
    override val state: StateFlow<HomeQuestsUiState> = MutableStateFlow(HomeQuestsUiState())
    override fun onCatalogClick(id: CatalogId) = Unit
}
