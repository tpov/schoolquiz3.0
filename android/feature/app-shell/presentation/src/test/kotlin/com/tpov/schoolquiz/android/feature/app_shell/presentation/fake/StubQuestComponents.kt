package com.tpov.schoolquiz.android.feature.app_shell.presentation.fake

import com.arkivanov.decompose.Child
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsUiState
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsUiState
import com.tpov.schoolquiz.android.feature.quest.presentation.DraftQuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object StubMyQuestsComponent : MyQuestsComponent {
    override val state: StateFlow<MyQuestsUiState> = MutableStateFlow(MyQuestsUiState())
    override fun onCatalogSelected(id: CatalogId?) = Unit
    override fun onCreateQuestClick() = Unit
    override fun onQuestClick(quest: QuestDisplayItem) = Unit
    override fun onDraftClick(draft: DraftQuestDisplayItem) = Unit
}

object StubHomeQuestsComponent : HomeQuestsComponent {
    override val state: StateFlow<HomeQuestsUiState> = MutableStateFlow(HomeQuestsUiState())
    override fun onCatalogClick(id: CatalogId, name: String) = Unit
}

object StubQuizzesComponent : QuizzesComponent {
    private val idleStack = ChildStack(
        active = Child.Created(configuration = QuizzesConfig.Idle, instance = QuizzesChild.Idle),
        backStack = emptyList(),
    )
    override val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>> =
        MutableValue(idleStack)
    override val currentCatalogName: StateFlow<String?> = MutableStateFlow(null)
    override val currentCatalogIcons: StateFlow<List<androidx.compose.ui.graphics.vector.ImageVector>> =
        MutableStateFlow(emptyList<androidx.compose.ui.graphics.vector.ImageVector>())

    override fun openQuestList(catalogId: CatalogId, catalogName: String) = Unit
    override fun openCourseArchive() = Unit
    override fun openCourseArena() = Unit
    override fun openPublicQuestCatalogPicker(targetShelf: String, title: String) = Unit
    override fun openPublicQuestShelfCatalog(
        targetShelf: String,
        title: String,
        forcedHardMode: Boolean?,
    ) = Unit
    override fun openSectionList(questId: QuestId, titles: List<String>) = Unit
    override fun dismissQuizzes() = Unit
    override fun popToLevel(uiLevel: Int) = Unit
    override fun popCurrentChild() = Unit
}
