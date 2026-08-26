package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestArenaTargetNode
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.UiMessage

@Composable
internal fun UiMessage.resolveText(): String =
    when (this) {
        is UiMessage.Res -> stringResource(id, *args.toTypedArray())
        is UiMessage.Raw -> value
        is UiMessage.ArenaQueued ->
            stringResource(
                R.string.qa_arena_queued,
                stringResource(target.labelRes),
                stringResource(if (toArchive) R.string.qa_dest_archive else R.string.qa_dest_arena),
            )
    }

internal val QuestArenaTargetNode.labelRes: Int
    @StringRes get() =
        when (this) {
            QuestArenaTargetNode.QUEST -> R.string.qa_node_quest
            QuestArenaTargetNode.SECTION -> R.string.qa_node_section
            QuestArenaTargetNode.THEME -> R.string.qa_node_theme
            QuestArenaTargetNode.LESSON -> R.string.qa_node_lesson
        }
