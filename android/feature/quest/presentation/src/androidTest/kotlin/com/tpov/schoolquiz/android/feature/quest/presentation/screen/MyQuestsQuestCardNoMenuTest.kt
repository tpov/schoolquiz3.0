package com.tpov.schoolquiz.android.feature.quest.presentation.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.DraftQuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsUiState
import com.tpov.schoolquiz.android.feature.quest.presentation.ui.MyQuestsScreen
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test: long press on QuestCard in MyQuestsScreen must NOT open any DropdownMenu.
 *
 * Spec traceability: docs/features/quizzes-screen/plan/phase-06/tests.md §MyQuestsQuestCardNoMenuTest
 * AC coverage: MY-UI-01 (AC#11) — existing MyQuestsScreen not modified; QuestCard(onLongClick=null).
 *
 * Verifies backward compatibility: the QuestCard change (Phase-02 added onLongClick param)
 * must remain backward-compatible — when onLongClick=null, no menu appears on long press.
 */
@RunWith(AndroidJUnit4::class)
class MyQuestsQuestCardNoMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fakeComponent(vararg quests: QuestDisplayItem): MyQuestsComponent {
        return object : MyQuestsComponent {
            override val state: StateFlow<MyQuestsUiState> = MutableStateFlow(
                MyQuestsUiState(quests = quests.toList()),
            )
            override fun onCatalogSelected(id: CatalogId?) = Unit
            override fun onCreateQuestClick() = Unit
            override fun onQuestClick(quest: QuestDisplayItem) = Unit
            override fun onDraftClick(draft: DraftQuestDisplayItem) = Unit
        }
    }

    // MY-UI-01
    // GIVEN MyQuestsScreen with a QuestCard in Loaded state
    // WHEN user long-presses the QuestCard
    // THEN "Поделиться" does not appear; node tagged "quest_menu" does not exist
    @Test
    fun myUi01_longPress_doesNotOpen_dropdownMenu() {
        val questA = QuestDisplayItem(
            id = QuestId("q-my"),
            catalogId = CatalogId("cat-1"),
            title = "Мой квест",
            pictureUrl = null,
            averageRating = null,
        )
        val component = fakeComponent(questA)

        composeTestRule.setContent {
            SchoolQuizTheme {
                MyQuestsScreen(component = component)
            }
        }

        composeTestRule.onNodeWithText("Мой квест").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Поделиться").assertDoesNotExist()
        composeTestRule.onNodeWithTag("quest_menu").assertDoesNotExist()
    }
}
