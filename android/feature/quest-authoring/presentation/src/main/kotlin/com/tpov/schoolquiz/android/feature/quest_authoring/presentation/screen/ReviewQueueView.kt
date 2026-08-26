package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentDetailUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentListItemUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewLanguagesUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQuestionUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueFilter
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueKindUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewSegmentLabelKind
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewSegmentUiState

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
fun ReviewQueueView(
    state: ReviewQueueUiState,
    onFilterMenuClick: () -> Unit,
    onFilterMenuDismiss: () -> Unit,
    onFilterSelected: (ReviewQueueFilter) -> Unit,
    onAssignmentSelected: (String) -> Unit,
    onBackToListClick: () -> Unit,
    onScoreSelected: (Int) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onTranslationTextChanged: (String, String, String) -> Unit,
    onSegmentAcceptedChanged: (String, String, Boolean) -> Unit,
    onSubmitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.detail == null) {
            ReviewQueueListHeader(
                state = state,
                onFilterMenuClick = onFilterMenuClick,
                onFilterMenuDismiss = onFilterMenuDismiss,
                onFilterSelected = onFilterSelected,
            )
            ReviewQueueList(
                state = state,
                onAssignmentSelected = onAssignmentSelected,
                modifier = Modifier.weight(1f),
            )
        } else {
            ReviewDetail(
                detail = state.detail,
                isSubmitting = state.isSubmitting,
                onBackToListClick = onBackToListClick,
                onScoreSelected = onScoreSelected,
                onLanguageSelected = onLanguageSelected,
                onTranslationTextChanged = onTranslationTextChanged,
                onSegmentAcceptedChanged = onSegmentAcceptedChanged,
                onSubmitClick = onSubmitClick,
                modifier = Modifier.weight(1f),
            )
        }
        state.errorMessage?.let {
            Text(text = it.resolveText(), color = NoirDanger)
        }
        state.successMessage?.let {
            Text(text = it.resolveText(), color = LocalNoirAccent.current)
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ReviewQueueListHeader(
    state: ReviewQueueUiState,
    onFilterMenuClick: () -> Unit,
    onFilterMenuDismiss: () -> Unit,
    onFilterSelected: (ReviewQueueFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.qa_review_title),
                style = NoirType.groupTitle,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.qa_review_assignments_count,
                        state.assignments.size,
                        state.assignments.size,
                    ),
                style = NoirType.rowSub,
                color = NoirT3,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            OutlinedButton(onClick = onFilterMenuClick) {
                Text(state.selectedFilter.displayName)
                Icon(Icons.Default.ExpandMore, contentDescription = null)
            }
            DropdownMenu(
                expanded = state.filterMenuExpanded,
                onDismissRequest = onFilterMenuDismiss,
            ) {
                state.availableFilters.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(filter.displayName) },
                        onClick = { onFilterSelected(filter) },
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ReviewQueueList(
    state: ReviewQueueUiState,
    onAssignmentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading ->
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        state.assignments.isEmpty() ->
            Text(
                text = stringResource(R.string.qa_review_empty),
                color = NoirT3,
                modifier = modifier,
            )
        else ->
            LazyColumn(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.assignments, key = { it.id }) { item ->
                    ReviewAssignmentCard(
                        item = item,
                        onClick = { onAssignmentSelected(item.id) },
                    )
                }
            }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ReviewAssignmentCard(
    item: ReviewAssignmentListItemUiState,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = NoirS1),
        modifier = Modifier.fillMaxWidth().testTag("review-assignment-${item.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = item.title, style = NoirType.groupTitle)
            Text(
                text = item.languages.label(),
                style = NoirType.rowSub,
                color = NoirT3,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.kinds.forEach { kind ->
                    AssistChip(onClick = {}, label = { Text(kind.displayName) })
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            pluralStringResource(
                                R.plurals.qa_review_questions_count,
                                item.questionCount,
                                item.questionCount,
                            ),
                        )
                    },
                )
            }
            ReviewScores(item)
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ReviewScores(item: ReviewAssignmentListItemUiState) {
    val scores =
        listOfNotNull(
            item.testingScore?.let { stringResource(R.string.qa_review_score_testing, it) },
            item.logicScore?.let { stringResource(R.string.qa_review_score_logic, it) },
            item.translationScore?.let { stringResource(R.string.qa_review_score_translation, it) },
        )
    if (scores.isNotEmpty()) {
        Text(
            text = scores.joinToString(separator = " · "),
            style = NoirType.kicker,
            color = NoirT3,
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun ReviewDetail(
    detail: ReviewAssignmentDetailUiState,
    isSubmitting: Boolean,
    onBackToListClick: () -> Unit,
    onScoreSelected: (Int) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onTranslationTextChanged: (String, String, String) -> Unit,
    onSegmentAcceptedChanged: (String, String, Boolean) -> Unit,
    onSubmitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackToListClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.qa_cd_back))
            }
            Column {
                Text(text = detail.title, style = NoirType.groupTitle)
                Text(
                    text = detail.kind.displayName,
                    style = NoirType.rowSub,
                    color = NoirT3,
                )
            }
        }
        if (detail.availableLanguages.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.availableLanguages.forEach { language ->
                    FilterChip(
                        selected = detail.selectedLanguage == language,
                        onClick = { onLanguageSelected(language) },
                        label = { Text(language) },
                    )
                }
            }
        }
        if (detail.kind == ReviewQueueKindUi.TESTING || detail.kind == ReviewQueueKindUi.LOGIC) {
            ScorePicker(selectedScore = detail.selectedScore, onScoreSelected = onScoreSelected)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(detail.questions, key = { it.id }) { question ->
                ReviewQuestionBlock(
                    question = question,
                    kind = detail.kind,
                    onTranslationTextChanged = onTranslationTextChanged,
                    onSegmentAcceptedChanged = onSegmentAcceptedChanged,
                )
            }
        }
        Button(
            onClick = onSubmitClick,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Text(
                text =
                    if (isSubmitting) {
                        stringResource(R.string.qa_review_submitting)
                    } else {
                        stringResource(R.string.qa_review_submit)
                    },
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ScorePicker(
    selectedScore: Int?,
    onScoreSelected: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        REVIEW_SCORE_VALUES.forEach { score ->
            TextButton(onClick = { onScoreSelected(score) }) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint =
                        if (selectedScore != null && score <= selectedScore) {
                            LocalNoirAccent.current
                        } else {
                            NoirT3
                        },
                )
                Text(score.toString())
            }
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun ReviewQuestionBlock(
    question: ReviewQuestionUiState,
    kind: ReviewQueueKindUi,
    onTranslationTextChanged: (String, String, String) -> Unit,
    onSegmentAcceptedChanged: (String, String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.qa_review_question_title, question.order + 1),
            style = NoirType.groupTitle,
        )
        Text(text = question.text, style = NoirType.rowSub)
        if (question.segments.isNotEmpty()) {
            question.segments.forEach { segment ->
                ReviewSegmentRow(
                    segment = segment,
                    kind = kind,
                    onTranslationTextChanged = onTranslationTextChanged,
                    onSegmentAcceptedChanged = onSegmentAcceptedChanged,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun ReviewSegmentRow(
    segment: ReviewSegmentUiState,
    kind: ReviewQueueKindUi,
    onTranslationTextChanged: (String, String, String) -> Unit,
    onSegmentAcceptedChanged: (String, String, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = segment.labelText(),
            style = NoirType.kicker,
            color = NoirT3,
        )
        Text(text = segment.sourceText, style = NoirType.rowSub)
        if (kind == ReviewQueueKindUi.TRANSLATION) {
            OutlinedTextField(
                value = segment.translatedText,
                onValueChange = { onTranslationTextChanged(segment.questionId, segment.key, it) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                label = { Text(stringResource(R.string.qa_review_translation_label)) },
            )
        } else if (kind == ReviewQueueKindUi.TRANSLATION_REVIEW) {
            val translatedText =
                if (segment.translatedText.isBlank()) {
                    stringResource(R.string.qa_review_no_translation)
                } else {
                    segment.translatedText
                }
            Text(
                text = translatedText,
                style = NoirType.rowSub,
                color =
                    if (segment.translatedText.isBlank()) {
                        NoirDanger
                    } else {
                        LocalNoirAccent.current
                    },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = segment.accepted,
                    onCheckedChange = { onSegmentAcceptedChanged(segment.questionId, segment.key, it) },
                )
                Text(stringResource(R.string.qa_review_accepted))
            }
        }
    }
}

@Composable
private fun ReviewSegmentUiState.labelText(): String =
    when (labelKind) {
        ReviewSegmentLabelKind.TEXT -> stringResource(R.string.qa_segment_text)
        ReviewSegmentLabelKind.OPTION -> stringResource(R.string.qa_segment_option, labelArg.orEmpty())
        ReviewSegmentLabelKind.ITEM -> stringResource(R.string.qa_segment_item, labelArg.orEmpty())
        ReviewSegmentLabelKind.CANDIDATE -> stringResource(R.string.qa_segment_candidate, labelArg.orEmpty())
        ReviewSegmentLabelKind.INFO -> stringResource(R.string.qa_segment_info)
    }

@Composable
private fun ReviewLanguagesUi.label(): String {
    val parts =
        buildList {
            if (source.isNotEmpty()) add(stringResource(R.string.qa_review_languages_from, source.joinToString()))
            if (translationTargets.isNotEmpty()) {
                add(stringResource(R.string.qa_review_languages_to, translationTargets.joinToString()))
            }
            if (reviewTargets.isNotEmpty()) {
                add(stringResource(R.string.qa_review_languages_review, reviewTargets.joinToString()))
            }
        }
    return parts.joinToString(separator = " / ").ifBlank { stringResource(R.string.qa_review_languages_none) }
}

private val ReviewQueueFilter.labelRes: Int
    @StringRes get() =
        when (this) {
            ReviewQueueFilter.ALL -> R.string.qa_filter_all
            ReviewQueueFilter.TESTING -> R.string.qa_filter_testing
            ReviewQueueFilter.LOGIC -> R.string.qa_filter_logic
            ReviewQueueFilter.TRANSLATION -> R.string.qa_filter_translation
            ReviewQueueFilter.TRANSLATION_REVIEW -> R.string.qa_filter_translation_review
        }

private val ReviewQueueFilter.displayName: String
    @Composable get() = stringResource(labelRes)

private val ReviewQueueKindUi.labelRes: Int
    @StringRes get() =
        when (this) {
            ReviewQueueKindUi.TESTING -> R.string.qa_filter_testing
            ReviewQueueKindUi.LOGIC -> R.string.qa_filter_logic
            ReviewQueueKindUi.TRANSLATION -> R.string.qa_filter_translation
            ReviewQueueKindUi.TRANSLATION_REVIEW -> R.string.qa_filter_translation_review
        }

private val ReviewQueueKindUi.displayName: String
    @Composable get() = stringResource(labelRes)

private const val MIN_REVIEW_SCORE = 1
private const val MAX_REVIEW_SCORE = 3
private val REVIEW_SCORE_VALUES = (MIN_REVIEW_SCORE..MAX_REVIEW_SCORE).toList()
