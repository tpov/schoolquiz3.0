package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.FillBlankAnswerItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestQuestionEditorUiState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * История правок редактора (UX-O6 §4, UX-18): единица — целый снимок состояния, поэтому текст и
 * его метки восстанавливаются вместе; новая правка обрывает redo; смена вопроса обрывает историю.
 */
class QuestionEditorHistoryTest {

    @Test
    fun `given fresh history when undo or redo then nothing moves`() {
        val history = QuestionEditorHistory()

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo(editorState(text = "текст")))
        assertNull(history.redo(editorState(text = "текст")))
    }

    @Test
    fun `given edits then undo restores text and annotations together`() {
        val history = QuestionEditorHistory()
        val before = editorState(
            fillBlankText = "JetBrains создал **Kotlin**",
            fillBlankAnswers = listOf(FillBlankAnswerItem(text = "Kotlin")),
        )

        history.push(before)
        val after = before.copy(
            fillBlankText = "JetBrains создал ***Kotlin***",
            fillBlankAnswers = listOf(FillBlankAnswerItem(text = "Kotlin", isProtected = true)),
        )
        assertTrue(history.canUndo, "правка запомнена")

        val restored = history.undo(after)

        assertEquals(before, restored, "текст и метка вернулись одним шагом")
        assertTrue(history.canRedo)
    }

    @Test
    fun `given undone edit then redo advances text and annotations together`() {
        val history = QuestionEditorHistory()
        val before =
            editorState(
                fillBlankText = "JetBrains создал **Kotlin**",
                fillBlankAnswers = listOf(FillBlankAnswerItem(text = "Kotlin")),
            )
        history.push(before)
        val after =
            before.copy(
                fillBlankText = "JetBrains создал ***Kotlin***",
                fillBlankAnswers = listOf(FillBlankAnswerItem(text = "Kotlin", isProtected = true)),
            )
        history.undo(after)

        val redone = history.redo(before)

        assertEquals(after, redone, "redo повторяет ту же связку текста и метки")
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `given a new edit after undo then the redo branch is cut off`() {
        val history = QuestionEditorHistory()
        history.push(editorState(text = "раз"))
        history.push(editorState(text = "два"))
        history.undo(editorState(text = "три"))

        history.push(history.undo(editorState(text = "два"))!!.copy(text = "два-исправлено"))

        assertFalse(history.canRedo, "ветка redo не переживает новую правку")
    }

    @Test
    fun `given more edits than the limit then the oldest step falls out`() {
        val history = QuestionEditorHistory(limit = 3)
        history.push(editorState(text = "1"))
        history.push(editorState(text = "2"))
        history.push(editorState(text = "3"))
        history.push(editorState(text = "4"))

        val restored = history.undo(editorState(text = "5"))

        assertEquals("4", restored?.text, "стек держит последние три снимка; «1» вытеснен")
        assertTrue(history.canUndo, "«2» ещё в истории")
    }

    @Test
    fun `given a cleared history then flags and stacks are empty`() {
        val history = QuestionEditorHistory()
        history.push(editorState(text = "раз"))
        history.clear()
        history.push(editorState(text = "два"))

        val restored = history.undo(editorState(text = "три"))

        assertEquals("два", restored?.text, "история прежней сессии не пережила clear")
    }

    private fun editorState(
        text: String = "",
        fillBlankText: String = "",
        fillBlankAnswers: List<FillBlankAnswerItem> = listOf(FillBlankAnswerItem()),
    ): QuestQuestionEditorUiState =
        QuestQuestionEditorUiState(
            draftId = QuestDraftId("draft-1"),
            draftTitle = "Черновик",
            lessonId = DraftLessonId("lesson-1"),
            type = DraftQuestionType.FILL_BLANK,
            difficulty = Difficulty.EASY,
            text = text,
            fillBlankText = fillBlankText,
            fillBlankAnswers = fillBlankAnswers,
        )
}
