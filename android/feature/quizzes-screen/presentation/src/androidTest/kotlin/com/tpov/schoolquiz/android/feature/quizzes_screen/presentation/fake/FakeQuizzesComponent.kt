package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.arkivanov.decompose.Child
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

class FakeQuizzesComponent : QuizzesComponent {

    private val _childStack = MutableValue(idleStack())
    override val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>> get() = _childStack

    var lastPoppedLevel: Int? = null

    fun setQuestListActive(catalogId: String = "cat-1", catalogName: String = "Математика") {
        val config = QuizzesConfig.QuestList(catalogId, listOf(catalogName))
        val fakeQuestList = FakeQuestListComponent(QuestListUiState.Loading, listOf(catalogName))
        _childStack.value = ChildStack(
            active = Child.Created(configuration = config, instance = QuizzesChild.QuestList(fakeQuestList)),
            backStack = emptyList(),
        )
    }

    override fun popToLevel(uiLevel: Int) {
        lastPoppedLevel = uiLevel
    }

    override fun openQuestList(catalogId: CatalogId, catalogName: String) {
        setQuestListActive(catalogId.value, catalogName)
    }

    override fun openSectionList(questId: QuestId, titles: List<String>) = Unit

    override fun dismissQuizzes() {
        _childStack.value = idleStack()
    }

    private fun idleStack(): ChildStack<QuizzesConfig, QuizzesChild> = ChildStack(
        active = Child.Created(configuration = QuizzesConfig.Idle, instance = QuizzesChild.Idle),
        backStack = emptyList(),
    )
}
