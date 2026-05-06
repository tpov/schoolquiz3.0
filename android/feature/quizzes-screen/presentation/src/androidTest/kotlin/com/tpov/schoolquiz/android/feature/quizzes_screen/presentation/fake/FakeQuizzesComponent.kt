package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.arkivanov.decompose.Child
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
import androidx.compose.ui.graphics.vector.ImageVector
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeQuizzesComponent : QuizzesComponent {

    private val _childStack = MutableValue(idleStack())
    override val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>> get() = _childStack

    private val _currentCatalogName = MutableStateFlow<String?>(null)
    override val currentCatalogName: StateFlow<String?> = _currentCatalogName.asStateFlow()

    private val _currentCatalogIcons = MutableStateFlow<List<ImageVector>>(emptyList())
    override val currentCatalogIcons: StateFlow<List<ImageVector>> = _currentCatalogIcons.asStateFlow()

    var lastPoppedLevel: Int? = null
    var popCurrentChildCalled: Int = 0

    fun setQuestListActive(catalogId: String = "cat-1", catalogName: String = "Математика") {
        val config = QuizzesConfig.QuestList(catalogId, listOf(catalogName))
        val fakeQuestList = FakeQuestListComponent(QuestListUiState.Loading, listOf(catalogName))
        _childStack.value = ChildStack(
            active = Child.Created(configuration = config, instance = QuizzesChild.QuestList(fakeQuestList)),
            backStack = emptyList(),
        )
        _currentCatalogName.value = catalogName
    }

    override fun popToLevel(uiLevel: Int) {
        lastPoppedLevel = uiLevel
    }

    override fun popCurrentChild() {
        popCurrentChildCalled++
    }

    override fun openQuestList(catalogId: CatalogId, catalogName: String) {
        setQuestListActive(catalogId.value, catalogName)
    }

    override fun openCourseArchive() {
        setQuestListActive("courses", "Курсы")
    }

    override fun openCourseArena() {
        setQuestListActive("courses", "Курсы")
    }

    override fun openSectionList(questId: QuestId, titles: List<String>) = Unit

    override fun dismissQuizzes() {
        _childStack.value = idleStack()
        _currentCatalogName.value = null
    }

    private fun idleStack(): ChildStack<QuizzesConfig, QuizzesChild> = ChildStack(
        active = Child.Created(configuration = QuizzesConfig.Idle, instance = QuizzesChild.Idle),
        backStack = emptyList(),
    )
}
