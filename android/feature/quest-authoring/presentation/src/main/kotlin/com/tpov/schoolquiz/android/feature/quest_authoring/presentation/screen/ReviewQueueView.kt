package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentDetailUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewAssignmentListItemUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQuestionUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueFilter
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueKindUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ReviewQueueUiState
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
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        state.successMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.primary)
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
                text = "Проверка",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${state.assignments.size} заданий",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                text = "Нет заданий",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("review-assignment-${item.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = item.languageLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.kindLabels.forEach { label ->
                    AssistChip(onClick = {}, label = { Text(label) })
                }
                AssistChip(onClick = {}, label = { Text("${item.questionCount} вопросов") })
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
            item.testingScore?.let { "Тест: $it" },
            item.logicScore?.let { "Валидация: $it" },
            item.translationScore?.let { "Перевод: $it" },
        )
    if (scores.isNotEmpty()) {
        Text(
            text = scores.joinToString(separator = " · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Column {
                Text(text = detail.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = detail.kindLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Text(text = if (isSubmitting) "Отправка" else "Отправить")
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
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
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
        Text(text = question.title, style = MaterialTheme.typography.titleMedium)
        Text(text = question.text, style = MaterialTheme.typography.bodyMedium)
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
            text = segment.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = segment.sourceText, style = MaterialTheme.typography.bodyMedium)
        if (kind == ReviewQueueKindUi.TRANSLATION) {
            OutlinedTextField(
                value = segment.translatedText,
                onValueChange = { onTranslationTextChanged(segment.questionId, segment.key, it) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                label = { Text("Перевод") },
            )
        } else if (kind == ReviewQueueKindUi.TRANSLATION_REVIEW) {
            val translatedText = segment.translatedText.ifBlank { "Нет перевода" }
            Text(
                text = translatedText,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (segment.translatedText.isBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = segment.accepted,
                    onCheckedChange = { onSegmentAcceptedChanged(segment.questionId, segment.key, it) },
                )
                Text("Принято")
            }
        }
    }
}

private val ReviewQueueFilter.displayName: String
    get() =
        when (this) {
            ReviewQueueFilter.ALL -> "Все"
            ReviewQueueFilter.TESTING -> "Тестирование"
            ReviewQueueFilter.LOGIC -> "Валидация"
            ReviewQueueFilter.TRANSLATION -> "Перевод"
            ReviewQueueFilter.TRANSLATION_REVIEW -> "Проверка перевода"
        }

private const val MIN_REVIEW_SCORE = 1
private const val MAX_REVIEW_SCORE = 3
private val REVIEW_SCORE_VALUES = (MIN_REVIEW_SCORE..MAX_REVIEW_SCORE).toList()
