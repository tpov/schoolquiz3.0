@file:Suppress("TooManyFunctions", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignCardShape
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignDeepSurfaceColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignLightBorderColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignNeutralBorderColor
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.FillBlankContent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.MultipleChoiceContent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.OrderingContent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.QuestionImage
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.SingleChoiceContent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.SurveyContent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.FILL_BLANK_RUNTIME_MARKER
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.FillBlankAnswerSpec
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.FillBlankVisualSegment
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.buildFillBlankRuntimeText
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.buildFillBlankVisualSegments
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.extractFillBlankAnswers
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.orderFillBlankAnswersByText
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DraftQuestionListItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.FillBlankAnswerItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.FillBlankMarkerKind
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestQuestionEditorUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.validationMessageRes
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId

private const val MAX_OPTION_ROWS = 8
private const val MIN_OPTION_ROWS = 2

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
fun QuestQuestionEditorContent(
    state: QuestQuestionEditorUiState,
    onBackClick: () -> Unit,
    onQuestionSelected: (Int) -> Unit,
    onPreviousQuestionClick: () -> Unit,
    onNextQuestionClick: () -> Unit,
    onAddQuestionClick: () -> Unit,
    onQuestionTypeSelected: (DraftQuestionType) -> Unit,
    onQuestionImageClick: () -> Unit,
    onQuestionPreviewClick: () -> Unit,
    onQuestionTextChanged: (String) -> Unit,
    onQuestionInfoChanged: (String) -> Unit,
    onOptionTextChanged: (index: Int, value: String) -> Unit,
    onOptionAdded: () -> Unit,
    onOptionRemoved: (Int) -> Unit,
    onSingleCorrectSelected: (Int) -> Unit,
    onMultipleCorrectToggled: (Int) -> Unit,
    onOrderingItemTextChanged: (index: Int, value: String) -> Unit,
    onOrderingItemAdded: () -> Unit,
    onOrderingItemRemoved: (Int) -> Unit,
    onFillBlankTextChanged: (String) -> Unit,
    onFillBlankMarkerAdded: (FillBlankMarkerKind) -> Unit,
    onFillBlankAnswerChanged: (index: Int, value: String) -> Unit,
    onFillBlankAnswerProtectedChanged: (index: Int, value: Boolean) -> Unit,
    onFillBlankAnswerAdded: () -> Unit,
    onFillBlankAnswerRemoved: (Int) -> Unit,
    onFillBlankDistractorChanged: (index: Int, value: String) -> Unit,
    onFillBlankDistractorAdded: () -> Unit,
    onFillBlankDistractorRemoved: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        EditorTopPanel(
            state = state,
            onBackClick = onBackClick,
            onQuestionImageClick = onQuestionImageClick,
            onQuestionPreviewClick = onQuestionPreviewClick,
        )
        if (state.isPreviewVisible) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                state.toPreviewState()?.let { previewState ->
                    RuntimePreview(
                        previewState = previewState,
                        modifier = Modifier.fillMaxSize(),
                    )
                } ?: FeedbackLine(state)
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TypeRail(
                    state = state,
                    onQuestionTypeSelected = onQuestionTypeSelected,
                )
                GenerationSurface(
                    state = state,
                    onQuestionTextChanged = onQuestionTextChanged,
                    onOptionTextChanged = onOptionTextChanged,
                    onOptionAdded = onOptionAdded,
                    onOptionRemoved = onOptionRemoved,
                    onSingleCorrectSelected = onSingleCorrectSelected,
                    onMultipleCorrectToggled = onMultipleCorrectToggled,
                    onOrderingItemTextChanged = onOrderingItemTextChanged,
                    onOrderingItemAdded = onOrderingItemAdded,
                    onOrderingItemRemoved = onOrderingItemRemoved,
                    onFillBlankTextChanged = onFillBlankTextChanged,
                    onFillBlankMarkerAdded = onFillBlankMarkerAdded,
                    onFillBlankAnswerChanged = onFillBlankAnswerChanged,
                    onFillBlankAnswerProtectedChanged = onFillBlankAnswerProtectedChanged,
                    onFillBlankAnswerAdded = onFillBlankAnswerAdded,
                    onFillBlankAnswerRemoved = onFillBlankAnswerRemoved,
                    onFillBlankDistractorChanged = onFillBlankDistractorChanged,
                    onFillBlankDistractorAdded = onFillBlankDistractorAdded,
                    onFillBlankDistractorRemoved = onFillBlankDistractorRemoved,
                )
                QuestionInfoPanel(
                    info = state.info,
                    onQuestionInfoChanged = onQuestionInfoChanged,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        EditorBottomBar(
            state = state,
            onBackClick = onBackClick,
            onQuestionSelected = onQuestionSelected,
            onPreviousQuestionClick = onPreviousQuestionClick,
            onNextQuestionClick = onNextQuestionClick,
            onAddQuestionClick = onAddQuestionClick,
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun EditorTopPanel(
    state: QuestQuestionEditorUiState,
    onBackClick: () -> Unit,
    onQuestionImageClick: () -> Unit,
    onQuestionPreviewClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = schoolQuizDesignDeepSurfaceColor(),
        contentColor = NoirT1,
        border = BorderStroke(1.dp, schoolQuizDesignNeutralBorderColor()),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.qa_cd_back_to_structure),
                    )
                }
                Text(
                    text = stringResource(R.string.qa_editor_question_number, state.displayQuestionNumber),
                    style = NoirType.groupTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onQuestionImageClick) {
                    Icon(Icons.Filled.Image, contentDescription = stringResource(R.string.qa_cd_add_photo))
                }
                IconButton(onClick = onQuestionPreviewClick) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription =
                            if (state.isPreviewVisible) {
                                stringResource(R.string.qa_cd_edit)
                            } else {
                                stringResource(R.string.qa_cd_preview)
                            },
                    )
                }
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
            FeedbackLine(state)
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TypeRail(
    state: QuestQuestionEditorUiState,
    onQuestionTypeSelected: (DraftQuestionType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DraftQuestionType.entries.forEach { type ->
            FilterChip(
                selected = state.type == type,
                onClick = { onQuestionTypeSelected(type) },
                label = { Text(type.displayTitle) },
            )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun GenerationSurface(
    state: QuestQuestionEditorUiState,
    onQuestionTextChanged: (String) -> Unit,
    onOptionTextChanged: (index: Int, value: String) -> Unit,
    onOptionAdded: () -> Unit,
    onOptionRemoved: (Int) -> Unit,
    onSingleCorrectSelected: (Int) -> Unit,
    onMultipleCorrectToggled: (Int) -> Unit,
    onOrderingItemTextChanged: (index: Int, value: String) -> Unit,
    onOrderingItemAdded: () -> Unit,
    onOrderingItemRemoved: (Int) -> Unit,
    onFillBlankTextChanged: (String) -> Unit,
    onFillBlankMarkerAdded: (FillBlankMarkerKind) -> Unit,
    onFillBlankAnswerChanged: (index: Int, value: String) -> Unit,
    onFillBlankAnswerProtectedChanged: (index: Int, value: Boolean) -> Unit,
    onFillBlankAnswerAdded: () -> Unit,
    onFillBlankAnswerRemoved: (Int) -> Unit,
    onFillBlankDistractorChanged: (index: Int, value: String) -> Unit,
    onFillBlankDistractorAdded: () -> Unit,
    onFillBlankDistractorRemoved: (Int) -> Unit,
) {
    RunnerLikeFrame(
        title = stringResource(R.string.qa_editor_question_number, state.displayQuestionNumber),
        subtitle = state.type.displayTitle,
        question = {
            if (state.type == DraftQuestionType.FILL_BLANK) {
                FillBlankQuestionCard(
                    state = state,
                    onFillBlankTextChanged = onFillBlankTextChanged,
                )
            } else {
                EditableQuestionCard(
                    text = state.text,
                    imagePath = state.imagePath,
                    onTextChanged = onQuestionTextChanged,
                )
            }
        },
    ) {
        when (state.type) {
            DraftQuestionType.SURVEY ->
                ChoiceGenerationEditor(
                    state = state,
                    multiple = true,
                    onOptionTextChanged = onOptionTextChanged,
                    onOptionAdded = onOptionAdded,
                    onOptionRemoved = onOptionRemoved,
                    onSingleCorrectSelected = onSingleCorrectSelected,
                    onMultipleCorrectToggled = onMultipleCorrectToggled,
                )
            DraftQuestionType.SINGLE_CHOICE ->
                ChoiceGenerationEditor(
                    state = state,
                    multiple = false,
                    onOptionTextChanged = onOptionTextChanged,
                    onOptionAdded = onOptionAdded,
                    onOptionRemoved = onOptionRemoved,
                    onSingleCorrectSelected = onSingleCorrectSelected,
                    onMultipleCorrectToggled = onMultipleCorrectToggled,
                )
            DraftQuestionType.MULTIPLE_CHOICE ->
                ChoiceGenerationEditor(
                    state = state,
                    multiple = true,
                    onOptionTextChanged = onOptionTextChanged,
                    onOptionAdded = onOptionAdded,
                    onOptionRemoved = onOptionRemoved,
                    onSingleCorrectSelected = onSingleCorrectSelected,
                    onMultipleCorrectToggled = onMultipleCorrectToggled,
                )
            DraftQuestionType.ORDERING ->
                OrderingGenerationEditor(
                    state = state,
                    onOrderingItemTextChanged = onOrderingItemTextChanged,
                    onOrderingItemAdded = onOrderingItemAdded,
                    onOrderingItemRemoved = onOrderingItemRemoved,
                )
            DraftQuestionType.FILL_BLANK ->
                FillBlankGenerationEditor(
                    state = state,
                    onFillBlankMarkerAdded = onFillBlankMarkerAdded,
                    onFillBlankAnswerChanged = onFillBlankAnswerChanged,
                    onFillBlankAnswerProtectedChanged = onFillBlankAnswerProtectedChanged,
                    onFillBlankAnswerAdded = onFillBlankAnswerAdded,
                    onFillBlankAnswerRemoved = onFillBlankAnswerRemoved,
                    onFillBlankDistractorChanged = onFillBlankDistractorChanged,
                    onFillBlankDistractorAdded = onFillBlankDistractorAdded,
                    onFillBlankDistractorRemoved = onFillBlankDistractorRemoved,
                )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun RunnerLikeFrame(
    title: String,
    subtitle: String,
    question: @Composable () -> Unit,
    answers: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = schoolQuizDesignCardShape(),
        color = NoirS1.copy(alpha = 0.24f),
        contentColor = NoirT1,
        border = BorderStroke(1.dp, LocalNoirAccent.current.copy(alpha = 0.52f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = NoirType.groupTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = NoirType.kicker,
                    color = LocalNoirAccent.current,
                )
            }
            HorizontalDivider(color = schoolQuizDesignLightBorderColor())
            question()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = answers,
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun EditableQuestionCard(
    text: String,
    imagePath: String,
    onTextChanged: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = schoolQuizDesignCardShape(),
        color = schoolQuizDesignDeepSurfaceColor(),
        contentColor = NoirT1,
        border = BorderStroke(1.dp, LocalNoirAccent.current.copy(alpha = 0.86f)),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorTextField(
                value = text,
                onValueChange = onTextChanged,
                label = stringResource(R.string.qa_editor_question_label),
                minLines = 3,
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            if (imagePath.isNotBlank()) {
                QuestionImage(
                    url = imagePath,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun FillBlankQuestionCard(
    state: QuestQuestionEditorUiState,
    onFillBlankTextChanged: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = schoolQuizDesignCardShape(),
        color = schoolQuizDesignDeepSurfaceColor(),
        contentColor = NoirT1,
        border = BorderStroke(1.dp, LocalNoirAccent.current.copy(alpha = 0.86f)),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorTextField(
                value = state.fillBlankText,
                onValueChange = onFillBlankTextChanged,
                label = stringResource(R.string.qa_editor_fill_blank_text_label),
                minLines = 3,
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.imagePath.isNotBlank()) {
                QuestionImage(
                    url = state.imagePath,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FillBlankTransformedPreview(state)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun FillBlankTransformedPreview(state: QuestQuestionEditorUiState) {
    if (state.fillBlankText.isBlank()) return
    val segments =
        buildFillBlankVisualSegments(
            text = state.fillBlankText,
            answers = emptyList(),
        )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = NoirS1.copy(alpha = 0.52f),
        contentColor = NoirT1,
        border = BorderStroke(1.dp, schoolQuizDesignLightBorderColor()),
    ) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            segments.forEach { segment ->
                if (segment.isBlank) {
                    val frameColor = segment.markerColor()
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = frameColor.copy(alpha = 0.16f),
                        contentColor = NoirT1,
                        border =
                            BorderStroke(
                                width = 1.5.dp,
                                color = frameColor,
                            ),
                    ) {
                        Text(
                            text = segment.text,
                            style = NoirType.rowSub,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    Text(
                        text = segment.text,
                        style = NoirType.rowSub,
                        color =
                            if (segment.isProtected) {
                                ProtectedMarkerColor
                            } else {
                                NoirT1
                            },
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun ChoiceGenerationEditor(
    state: QuestQuestionEditorUiState,
    multiple: Boolean,
    onOptionTextChanged: (index: Int, value: String) -> Unit,
    onOptionAdded: () -> Unit,
    onOptionRemoved: (Int) -> Unit,
    onSingleCorrectSelected: (Int) -> Unit,
    onMultipleCorrectToggled: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.optionTexts.forEachIndexed { index, value ->
            AnswerRow(
                index = index,
                value = value,
                selected =
                    if (multiple) {
                        index in state.correctMultipleIndexes
                    } else {
                        state.correctSingleIndex == index
                    },
                multiple = multiple,
                canRemove = state.optionTexts.size > MIN_OPTION_ROWS,
                onTextChanged = { onOptionTextChanged(index, it) },
                onSelected = {
                    if (multiple) {
                        onMultipleCorrectToggled(index)
                    } else {
                        onSingleCorrectSelected(index)
                    }
                },
                onRemove = { onOptionRemoved(index) },
            )
        }
        AddRowButton(
            text = stringResource(R.string.qa_editor_add_option),
            enabled = state.optionTexts.size < MAX_OPTION_ROWS,
            onClick = onOptionAdded,
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun AnswerRow(
    index: Int,
    value: String,
    selected: Boolean,
    multiple: Boolean,
    canRemove: Boolean,
    onTextChanged: (String) -> Unit,
    onSelected: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color =
            if (selected) {
                LocalNoirAccent.current.copy(alpha = 0.16f)
            } else {
                schoolQuizDesignDeepSurfaceColor()
            },
        contentColor = NoirT1,
        border =
            BorderStroke(
                width = if (selected) 2.dp else 1.5.dp,
                color =
                    if (selected) {
                        LocalNoirAccent.current
                    } else {
                        schoolQuizDesignLightBorderColor()
                    },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (multiple) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onSelected() },
                )
            } else {
                RadioButton(
                    selected = selected,
                    onClick = onSelected,
                )
            }
            EditorTextField(
                value = value,
                onValueChange = onTextChanged,
                label = stringResource(R.string.qa_editor_option_number, index + 1),
                modifier = Modifier.weight(1f),
            )
            RemoveRowButton(
                enabled = canRemove,
                onClick = onRemove,
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun OrderingGenerationEditor(
    state: QuestQuestionEditorUiState,
    onOrderingItemTextChanged: (index: Int, value: String) -> Unit,
    onOrderingItemAdded: () -> Unit,
    onOrderingItemRemoved: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.orderingItems.forEachIndexed { index, value ->
            OrderedItemRow(
                index = index,
                value = value,
                canRemove = state.orderingItems.size > MIN_OPTION_ROWS,
                onTextChanged = { onOrderingItemTextChanged(index, it) },
                onRemove = { onOrderingItemRemoved(index) },
            )
        }
        AddRowButton(
            text = stringResource(R.string.qa_editor_add_item),
            enabled = state.orderingItems.size < MAX_OPTION_ROWS,
            onClick = onOrderingItemAdded,
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun OrderedItemRow(
    index: Int,
    value: String,
    canRemove: Boolean,
    onTextChanged: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = schoolQuizDesignCardShape(),
        color = NoirS1.copy(alpha = 0.52f),
        contentColor = NoirT1,
        border = BorderStroke(1.dp, schoolQuizDesignLightBorderColor()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = schoolQuizDesignCardShape(),
                color = LocalNoirAccent.current.copy(alpha = 0.14f),
                contentColor = LocalNoirAccent.current,
                border = BorderStroke(1.dp, LocalNoirAccent.current.copy(alpha = 0.56f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (index + 1).toString(),
                        style = NoirType.rowTitle,
                    )
                }
            }
            EditorTextField(
                value = value,
                onValueChange = onTextChanged,
                label = stringResource(R.string.qa_editor_item_label),
                modifier = Modifier.weight(1f),
            )
            RemoveRowButton(
                enabled = canRemove,
                onClick = onRemove,
            )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun FillBlankGenerationEditor(
    state: QuestQuestionEditorUiState,
    onFillBlankMarkerAdded: (FillBlankMarkerKind) -> Unit,
    onFillBlankAnswerChanged: (index: Int, value: String) -> Unit,
    onFillBlankAnswerProtectedChanged: (index: Int, value: Boolean) -> Unit,
    onFillBlankAnswerAdded: () -> Unit,
    onFillBlankAnswerRemoved: (Int) -> Unit,
    onFillBlankDistractorChanged: (index: Int, value: String) -> Unit,
    onFillBlankDistractorAdded: () -> Unit,
    onFillBlankDistractorRemoved: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarkerButton(
                text = "**blank**",
                color = BlankMarkerColor,
                onClick = { onFillBlankMarkerAdded(FillBlankMarkerKind.BLANK) },
                modifier = Modifier.weight(1f),
            )
            MarkerButton(
                text = "*text*",
                color = ProtectedMarkerColor,
                onClick = { onFillBlankMarkerAdded(FillBlankMarkerKind.PROTECTED) },
                modifier = Modifier.weight(1f),
            )
        }
        MarkerButton(
            text = "***blank***",
            color = BlankProtectedMarkerColor,
            onClick = { onFillBlankMarkerAdded(FillBlankMarkerKind.BLANK_PROTECTED) },
            modifier = Modifier.fillMaxWidth(),
        )
        FillBlankAnswerSection(
            answers = state.fillBlankAnswers,
            onAnswerChanged = onFillBlankAnswerChanged,
            onAnswerProtectedChanged = onFillBlankAnswerProtectedChanged,
            onAnswerAdded = onFillBlankAnswerAdded,
            onAnswerRemoved = onFillBlankAnswerRemoved,
        )
        FillBlankDistractorSection(
            distractors = state.fillBlankDistractors,
            correctCount = state.fillBlankAnswers.size,
            onDistractorChanged = onFillBlankDistractorChanged,
            onDistractorAdded = onFillBlankDistractorAdded,
            onDistractorRemoved = onFillBlankDistractorRemoved,
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun FillBlankAnswerSection(
    answers: List<FillBlankAnswerItem>,
    onAnswerChanged: (index: Int, value: String) -> Unit,
    onAnswerProtectedChanged: (index: Int, value: Boolean) -> Unit,
    onAnswerAdded: () -> Unit,
    onAnswerRemoved: (Int) -> Unit,
) {
    EditorPanel {
        Text(
            text = stringResource(R.string.qa_editor_correct_answers_title),
            style = NoirType.rowTitle,
            color = LocalNoirAccent.current,
        )
        answers.forEachIndexed { index, answer ->
            FillBlankAnswerRow(
                index = index,
                answer = answer,
                canRemove = answers.size > 1,
                onTextChanged = { onAnswerChanged(index, it) },
                onProtectedChanged = { onAnswerProtectedChanged(index, it) },
                onRemove = { onAnswerRemoved(index) },
            )
        }
        AddRowButton(
            text = stringResource(R.string.qa_editor_add_correct),
            enabled = answers.size < 3,
            onClick = onAnswerAdded,
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun FillBlankAnswerRow(
    index: Int,
    answer: FillBlankAnswerItem,
    canRemove: Boolean,
    onTextChanged: (String) -> Unit,
    onProtectedChanged: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = schoolQuizDesignDeepSurfaceColor(),
        contentColor = NoirT1,
        border = BorderStroke(1.dp, schoolQuizDesignLightBorderColor()),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EditorTextField(
                    value = answer.text,
                    onValueChange = onTextChanged,
                    label = stringResource(R.string.qa_editor_correct_answer_number, index + 1),
                    modifier = Modifier.weight(1f),
                )
                RemoveRowButton(
                    enabled = canRemove,
                    onClick = onRemove,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Checkbox(
                    checked = answer.isProtected,
                    onCheckedChange = onProtectedChanged,
                )
                Text(
                    text = stringResource(R.string.qa_editor_do_not_translate),
                    style = NoirType.rowSub,
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun FillBlankDistractorSection(
    distractors: List<String>,
    correctCount: Int,
    onDistractorChanged: (index: Int, value: String) -> Unit,
    onDistractorAdded: () -> Unit,
    onDistractorRemoved: (Int) -> Unit,
) {
    EditorPanel {
        Text(
            text = stringResource(R.string.qa_editor_distractors_title),
            style = NoirType.rowTitle,
            color = LocalNoirAccent.current,
        )
        distractors.forEachIndexed { index, value ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EditorTextField(
                    value = value,
                    onValueChange = { onDistractorChanged(index, it) },
                    label = stringResource(R.string.qa_editor_distractor_number, index + 1),
                    modifier = Modifier.weight(1f),
                )
                RemoveRowButton(
                    enabled = true,
                    onClick = { onDistractorRemoved(index) },
                )
            }
        }
        AddRowButton(
            text = stringResource(R.string.qa_editor_add_distractor),
            enabled = correctCount + distractors.size < 10,
            onClick = onDistractorAdded,
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun MarkerButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.14f),
        contentColor = NoirT1,
        border = BorderStroke(1.5.dp, color),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = NoirType.rowSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun AddRowButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = false,
        enabled = enabled,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun RemoveRowButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(
            text = "-",
            style = NoirType.groupTitle,
            color =
                if (enabled) {
                    NoirDanger
                } else {
                    NoirT3
                },
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun QuestionInfoPanel(
    info: String,
    onQuestionInfoChanged: (String) -> Unit,
) {
    EditorPanel {
        EditorTextField(
            value = info,
            onValueChange = onQuestionInfoChanged,
            label = stringResource(R.string.qa_editor_info_label),
            minLines = 2,
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun RuntimePreview(
    previewState: QuestionUiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (previewState) {
            is QuestionUiState.Survey ->
                SurveyContent(
                    state = previewState,
                    onOptionToggled = {},
                    onSubmit = {},
                )
            is QuestionUiState.SingleChoice ->
                SingleChoiceContent(
                    state = previewState,
                    onOptionSelected = {},
                    modifier = Modifier.fillMaxSize(),
                )
            is QuestionUiState.MultipleChoice ->
                MultipleChoiceContent(
                    state = previewState,
                    onOptionToggled = {},
                    onSubmit = {},
                    modifier = Modifier.fillMaxSize(),
                )
            is QuestionUiState.Ordering ->
                OrderingContent(
                    state = previewState,
                    onMoveUp = {},
                    onMoveDown = {},
                    onReorder = { _, _ -> },
                    onSubmit = {},
                    modifier = Modifier.fillMaxSize(),
                )
            is QuestionUiState.FillBlank ->
                FillBlankContent(
                    state = previewState,
                    candidates = previewState.candidates,
                    usedCandidateIds = emptySet(),
                    onCandidateSelected = { _, _ -> },
                    onBlankCleared = {},
                    onSubmit = {},
                    modifier = Modifier.fillMaxSize(),
                )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun EditorBottomBar(
    state: QuestQuestionEditorUiState,
    onBackClick: () -> Unit,
    onQuestionSelected: (Int) -> Unit,
    onPreviousQuestionClick: () -> Unit,
    onNextQuestionClick: () -> Unit,
    onAddQuestionClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = schoolQuizDesignDeepSurfaceColor(),
        contentColor = NoirT1,
        border = BorderStroke(1.dp, schoolQuizDesignNeutralBorderColor()),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.qa_cd_back))
                }
                IconButton(
                    onClick = onPreviousQuestionClick,
                    enabled = state.canGoPrevious,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.qa_cd_previous_question),
                    )
                }
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.questionItems.forEachIndexed { index, item ->
                        QuestionChip(
                            item = item,
                            selected = state.activeIndex == index,
                            onClick = { onQuestionSelected(index) },
                        )
                    }
                    if (state.isNewQuestion) {
                        NewQuestionChip(
                            selected = true,
                            number = state.questionItems.size + 1,
                            onClick = { onQuestionSelected(state.questionItems.size) },
                        )
                    }
                }
                IconButton(
                    onClick = onNextQuestionClick,
                    enabled = state.canGoNext,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.qa_cd_next_question),
                    )
                }
                IconButton(onClick = onAddQuestionClick) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.qa_cd_add_question))
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun QuestionChip(
    item: DraftQuestionListItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text =
                    if (item.title.isBlank()) {
                        "${item.number}. ${item.type.displayTitle}"
                    } else {
                        "${item.number}. ${item.title}"
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
        },
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun NewQuestionChip(
    selected: Boolean,
    number: Int,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(R.string.qa_editor_new_question_number, number)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun FeedbackLine(state: QuestQuestionEditorUiState) {
    val error = state.errorMessage?.resolveText()
    val saved = state.lastSavedMessage?.resolveText()
    val validation = state.validationMessageRes()?.let { stringResource(it) }
    val message = error ?: saved ?: validation ?: return

    val isError = error != null || (!state.canSave && saved == null)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!isError && state.lastSavedMessage != null) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = LocalNoirAccent.current,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = message,
            color =
                if (isError) {
                    NoirDanger
                } else {
                    LocalNoirAccent.current
                },
            style = NoirType.rowSub,
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun EditorPanel(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = schoolQuizDesignCardShape(),
        color = schoolQuizDesignDeepSurfaceColor(),
        contentColor = NoirT1,
        border = BorderStroke(1.dp, schoolQuizDesignNeutralBorderColor()),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        shape = schoolQuizDesignCardShape(),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = NoirT1,
                unfocusedTextColor = NoirT1,
                focusedContainerColor = schoolQuizDesignDeepSurfaceColor(),
                unfocusedContainerColor = schoolQuizDesignDeepSurfaceColor(),
                focusedBorderColor = LocalNoirAccent.current,
                unfocusedBorderColor = schoolQuizDesignLightBorderColor(),
                disabledBorderColor = schoolQuizDesignNeutralBorderColor(),
            ),
        modifier = modifier.heightIn(min = if (singleLine) 0.dp else 96.dp),
    )
}

internal val DraftQuestionType.labelRes: Int
    @StringRes get() =
        when (this) {
            DraftQuestionType.SINGLE_CHOICE -> R.string.qa_type_single_choice
            DraftQuestionType.MULTIPLE_CHOICE -> R.string.qa_type_multiple_choice
            DraftQuestionType.ORDERING -> R.string.qa_type_ordering
            DraftQuestionType.FILL_BLANK -> R.string.qa_type_fill_blank
            DraftQuestionType.SURVEY -> R.string.qa_type_survey
        }

private val DraftQuestionType.displayTitle: String
    @Composable get() = stringResource(labelRes)

private const val BLANK_MARKER_COLOR_ARGB = 0xFFFFC107
private const val PROTECTED_MARKER_COLOR_ARGB = 0xFF2196F3
private const val BLANK_PROTECTED_MARKER_COLOR_ARGB = 0xFF4CAF50

private val BlankMarkerColor = Color(BLANK_MARKER_COLOR_ARGB)
private val ProtectedMarkerColor = Color(PROTECTED_MARKER_COLOR_ARGB)
private val BlankProtectedMarkerColor = Color(BLANK_PROTECTED_MARKER_COLOR_ARGB)

private fun FillBlankVisualSegment.markerColor(): Color =
    when {
        isBlank && isProtected -> BlankProtectedMarkerColor
        isBlank -> BlankMarkerColor
        else -> ProtectedMarkerColor
    }

private fun QuestQuestionEditorUiState.validationMessageRes(): Int? =
    if (shouldHideValidationMessage()) {
        null
    } else {
        type.validationMessageRes()
    }

private fun QuestQuestionEditorUiState.shouldHideValidationMessage(): Boolean =
    canSave || isSaving || errorMessage != null || lastSavedMessage != null || isEmptyNewQuestion()

private fun QuestQuestionEditorUiState.isEmptyNewQuestion(): Boolean =
    selectedQuestionId == null &&
        text.isBlank() &&
        imagePath.isBlank() &&
        info.isBlank() &&
        optionTexts.all { it.isBlank() } &&
        orderingItems.all { it.isBlank() } &&
        fillBlankText.isBlank() &&
        fillBlankAnswers.all { it.text.isBlank() } &&
        fillBlankDistractors.all { it.isBlank() }

private fun QuestQuestionEditorUiState.toPreviewState(): QuestionUiState? =
    when (type) {
        DraftQuestionType.SURVEY -> {
            val options = optionTexts.toOptionUi("opt")
            if (text.isBlank() || options.isEmpty()) {
                null
            } else {
                QuestionUiState.Survey(
                    questionText = text,
                    hasImage = imagePath.isNotBlank(),
                    imageUrl = imagePath.takeIf { it.startsWith("https://") },
                    options = options,
                    selectedIds = emptySet(),
                    allowMultiple = correctMultipleIndexes.size > 1,
                    info = info.takeIf { it.isNotBlank() },
                )
            }
        }
        DraftQuestionType.SINGLE_CHOICE -> {
            val options = optionTexts.toOptionUi("opt")
            if (text.isBlank() || options.isEmpty()) {
                null
            } else {
                QuestionUiState.SingleChoice(
                    questionText = text,
                    hasImage = imagePath.isNotBlank(),
                    imageUrl = imagePath.ifBlank { null },
                    options = options,
                    selectedOptionId = null,
                )
            }
        }
        DraftQuestionType.MULTIPLE_CHOICE -> {
            val options = optionTexts.toOptionUi("opt")
            if (text.isBlank() || options.isEmpty()) {
                null
            } else {
                QuestionUiState.MultipleChoice(
                    questionText = text,
                    hasImage = imagePath.isNotBlank(),
                    imageUrl = imagePath.ifBlank { null },
                    options = options,
                    selectedIds = emptySet(),
                )
            }
        }
        DraftQuestionType.ORDERING -> {
            val items = orderingItems.toOptionUi("ord")
            if (text.isBlank() || items.isEmpty()) {
                null
            } else {
                QuestionUiState.Ordering(
                    questionText = text,
                    hasImage = imagePath.isNotBlank(),
                    imageUrl = imagePath.ifBlank { null },
                    items = items,
                )
            }
        }
        DraftQuestionType.FILL_BLANK -> {
            val orderedAnswers = resolveFillBlankAnswers() ?: return null
            val candidates =
                (orderedAnswers.map { it.text } + fillBlankDistractors)
                    .distinctBy { it.trim().lowercase() }
                    .toOptionUi("cand")
            val runtimeText =
                buildFillBlankRuntimeText(
                    text = fillBlankText,
                    answers = orderedAnswers,
                )
            if (runtimeText == null || candidates.isEmpty()) {
                null
            } else {
                QuestionUiState.FillBlank(
                    questionText = runtimeText,
                    hasImage = imagePath.isNotBlank(),
                    imageUrl = imagePath.ifBlank { null },
                    templateParts = runtimeText.toTemplateParts(orderedAnswers.size),
                    filledValues = emptyMap(),
                    candidates = candidates,
                    correctCandidateIdsByBlankIndex = orderedAnswers.indices.associateWith { "cand-$it" },
                )
            }
        }
    }

private fun QuestQuestionEditorUiState.resolveFillBlankAnswers(): List<FillBlankAnswerSpec>? {
    val markupAnswers = extractFillBlankAnswers(fillBlankText)
    if (markupAnswers.isNotEmpty()) return markupAnswers

    val manualAnswers =
        fillBlankAnswers.mapNotNull { answer ->
            answer.text.trim().takeIf { it.isNotEmpty() }?.let { text ->
                FillBlankAnswerSpec(
                    text = text,
                    isProtected = answer.isProtected,
                )
            }
        }
    return orderFillBlankAnswersByText(
        text = fillBlankText,
        answers = manualAnswers,
    )
}

private fun List<String>.toOptionUi(prefix: String): List<OptionUi> =
    mapIndexedNotNull { index, value ->
        value.trim().takeIf { it.isNotEmpty() }?.let { text ->
            OptionUi(id = "$prefix-$index", text = text)
        }
    }

private fun String.toTemplateParts(blankCount: Int): List<TemplatePart> {
    val segments = split(FILL_BLANK_RUNTIME_MARKER)
    val result = mutableListOf<TemplatePart>()
    segments.forEachIndexed { index, segment ->
        if (segment.isNotEmpty()) result.add(TemplatePart.Text(segment))
        if (index < segments.size - 1 && index < blankCount) {
            result.add(
                TemplatePart.Blank(
                    index = index,
                    placeholder = FILL_BLANK_RUNTIME_MARKER,
                    blankId = "blank-$index",
                ),
            )
        }
    }
    return result.ifEmpty { listOf(TemplatePart.Text(this)) }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun QuestQuestionEditorContentPreview() {
    SchoolQuizTheme {
        QuestQuestionEditorContent(
            state =
                QuestQuestionEditorUiState(
                    draftId = QuestDraftId("draft-1"),
                    draftTitle = "Мой квест",
                    lessonId = DraftLessonId("lesson-1"),
                    questionItems =
                        listOf(
                            DraftQuestionListItem(
                                id = DraftQuestionId("question-1"),
                                number = 1,
                                title = "Первый вопрос",
                                type = DraftQuestionType.SINGLE_CHOICE,
                                difficulty = Difficulty.EASY,
                            ),
                        ),
                    text = "Какой язык создан JetBrains?",
                    optionTexts = listOf("Kotlin", "Java", "Swift", "Go"),
                ),
            onBackClick = {},
            onQuestionSelected = {},
            onPreviousQuestionClick = {},
            onNextQuestionClick = {},
            onAddQuestionClick = {},
            onQuestionTypeSelected = {},
            onQuestionImageClick = {},
            onQuestionPreviewClick = {},
            onQuestionTextChanged = {},
            onQuestionInfoChanged = {},
            onOptionTextChanged = { _, _ -> },
            onOptionAdded = {},
            onOptionRemoved = {},
            onSingleCorrectSelected = {},
            onMultipleCorrectToggled = {},
            onOrderingItemTextChanged = { _, _ -> },
            onOrderingItemAdded = {},
            onOrderingItemRemoved = {},
            onFillBlankTextChanged = {},
            onFillBlankMarkerAdded = {},
            onFillBlankAnswerChanged = { _, _ -> },
            onFillBlankAnswerProtectedChanged = { _, _ -> },
            onFillBlankAnswerAdded = {},
            onFillBlankAnswerRemoved = {},
            onFillBlankDistractorChanged = { _, _ -> },
            onFillBlankDistractorAdded = {},
            onFillBlankDistractorRemoved = {},
        )
    }
}
