package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.BrandCard
import com.tpov.schoolquiz.android.core.designsystem.components.BrandPrimaryButton
import com.tpov.schoolquiz.android.core.designsystem.components.CatalogSpinner
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignCardShape
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignDeepSurfaceColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignLightBorderColor
import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.QuestCreateComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.LessonPathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestCreateUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestPathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.SectionPathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ThemePathItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun QuestCreateScreen(
    component: QuestCreateComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()
    val context = LocalContext.current
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                component.onQuestionImagePathChanged(it.toString())
            }
        }

    val editor = state.editor
    if (editor != null) {
        QuestQuestionEditorContent(
            state = editor,
            onBackClick = component::onBackToStructureClick,
            onQuestionSelected = component::onQuestionSelected,
            onPreviousQuestionClick = component::onPreviousQuestionClick,
            onNextQuestionClick = component::onNextQuestionClick,
            onAddQuestionClick = component::onAddQuestionClick,
            onQuestionTypeSelected = component::onQuestionTypeSelected,
            onQuestionImageClick = { imagePicker.launch(arrayOf("image/*")) },
            onQuestionPreviewClick = component::onQuestionPreviewClick,
            onQuestionTextChanged = component::onQuestionTextChanged,
            onQuestionInfoChanged = component::onQuestionInfoChanged,
            onOptionTextChanged = component::onOptionTextChanged,
            onOptionAdded = component::onOptionAdded,
            onOptionRemoved = component::onOptionRemoved,
            onSingleCorrectSelected = component::onSingleCorrectSelected,
            onMultipleCorrectToggled = component::onMultipleCorrectToggled,
            onOrderingItemTextChanged = component::onOrderingItemTextChanged,
            onOrderingItemAdded = component::onOrderingItemAdded,
            onOrderingItemRemoved = component::onOrderingItemRemoved,
            onFillBlankTextChanged = component::onFillBlankTextChanged,
            onFillBlankMarkerAdded = component::onFillBlankMarkerAdded,
            onFillBlankAnswerChanged = component::onFillBlankAnswerChanged,
            onFillBlankAnswerProtectedChanged = component::onFillBlankAnswerProtectedChanged,
            onFillBlankAnswerAdded = component::onFillBlankAnswerAdded,
            onFillBlankAnswerRemoved = component::onFillBlankAnswerRemoved,
            onFillBlankDistractorChanged = component::onFillBlankDistractorChanged,
            onFillBlankDistractorAdded = component::onFillBlankDistractorAdded,
            onFillBlankDistractorRemoved = component::onFillBlankDistractorRemoved,
            modifier = modifier,
        )
    } else {
        QuestCreateContent(
            state = state,
            onBackClick = component::onBackClick,
            onCatalogSelected = component::onCatalogSelected,
            onQuestSelected = component::onQuestSelected,
            onSectionSelected = component::onSectionSelected,
            onThemeSelected = component::onThemeSelected,
            onLessonSelected = component::onLessonSelected,
            onQuestTitleChanged = component::onTitleChanged,
            onSectionTitleChanged = component::onSectionTitleChanged,
            onThemeTitleChanged = component::onThemeTitleChanged,
            onLessonTitleChanged = component::onLessonTitleChanged,
            onLanguageChanged = component::onLanguageChanged,
            onStructureCheckClick = component::onStructureCheckClick,
            onContinueDraftClick = component::onContinueDraftClick,
            onSubmitToArenaClick = component::onSubmitToArenaClick,
            onQuestionsClick = component::onQuestionsClick,
            modifier = modifier,
        )
    }
}

@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun QuestCreateContent(
    state: QuestCreateUiState,
    onBackClick: () -> Unit,
    onCatalogSelected: (CatalogId?) -> Unit,
    onQuestSelected: (QuestId?) -> Unit,
    onSectionSelected: (SectionId?) -> Unit,
    onThemeSelected: (ThemeId?) -> Unit,
    onLessonSelected: (LessonId?) -> Unit,
    onQuestTitleChanged: (String) -> Unit,
    onSectionTitleChanged: (String) -> Unit,
    onThemeTitleChanged: (String) -> Unit,
    onLessonTitleChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onStructureCheckClick: () -> Unit,
    onContinueDraftClick: () -> Unit,
    onSubmitToArenaClick: () -> Unit,
    onQuestionsClick: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isStructureActionEnabled = if (state.hasActiveDraft) state.canContinueDraft else state.canCreate
    val isQuestionActionEnabled = state.canOpenQuestions

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                text = "Создание квеста",
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
            )
            if (state.isCreating || state.isSubmittingToArena) {
                CircularProgressIndicator(modifier = Modifier.padding(horizontal = 12.dp))
            } else {
                IconButton(
                    onClick = onStructureCheckClick,
                    enabled = isStructureActionEnabled,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Сохранить структуру",
                        tint =
                            if (isStructureActionEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }

        state.activeDraftTitle?.let { title ->
            AssistChip(
                onClick = onContinueDraftClick,
                label = { Text("Черновик: $title") },
            )
        }

        BrandCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CatalogSpinner(
                    items = state.catalogs,
                    selectedId = state.selectedCatalogId,
                    onSelectionChanged = onCatalogSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                QuestPathSpinner(
                    items = state.questItems,
                    selectedId = state.selectedQuestId,
                    createTitle = state.newQuestTitle,
                    onSelectionChanged = onQuestSelected,
                    onCreateTitleChanged = onQuestTitleChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionPathSpinner(
                    items = state.sectionItems,
                    selectedId = state.selectedSectionId,
                    createTitle = state.newSectionTitle,
                    onSelectionChanged = onSectionSelected,
                    onCreateTitleChanged = onSectionTitleChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
                ThemePathSpinner(
                    items = state.themeItems,
                    selectedId = state.selectedThemeId,
                    createTitle = state.newThemeTitle,
                    onSelectionChanged = onThemeSelected,
                    onCreateTitleChanged = onThemeTitleChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
                LessonPathSpinner(
                    items = state.lessonItems,
                    selectedId = state.selectedLessonId,
                    createTitle = state.newLessonTitle,
                    onSelectionChanged = onLessonSelected,
                    onCreateTitleChanged = onLessonTitleChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        BrandCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LanguageChipRow(
                    languages = state.availableLanguages,
                    selectedLanguage = state.defaultLanguage,
                    onLanguageSelected = onLanguageChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.arenaMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrandPrimaryButton(
                text =
                    when {
                        state.isCreating -> "Сохраняю"
                        else -> "Легкие вопросы"
                    },
                onClick = { onQuestionsClick(Difficulty.EASY) },
                enabled = isQuestionActionEnabled,
                modifier = Modifier.weight(1f),
            )
            BrandPrimaryButton(
                text =
                    when {
                        state.isCreating -> "Сохраняю"
                        else -> "Сложные вопросы"
                    },
                onClick = { onQuestionsClick(Difficulty.HARD) },
                enabled = isQuestionActionEnabled,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.hasActiveDraft) {
            BrandPrimaryButton(
                text =
                    when {
                        state.isSubmittingToArena -> "Отправляю"
                        else -> "Отправить на арену"
                    },
                onClick = onSubmitToArenaClick,
                enabled = state.canSubmitToArena,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun QuestPathSpinner(
    items: List<QuestPathItem>,
    selectedId: QuestId?,
    createTitle: String,
    onSelectionChanged: (QuestId?) -> Unit,
    onCreateTitleChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StructureDropdown(
        label = "Квест",
        createPlaceholder = "Создать квест",
        options = items.map { StructureOption(id = it.id, title = it.title) },
        selectedId = selectedId,
        createTitle = createTitle,
        onSelectionChanged = onSelectionChanged,
        onCreateTitleChanged = onCreateTitleChanged,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun SectionPathSpinner(
    items: List<SectionPathItem>,
    selectedId: SectionId?,
    createTitle: String,
    onSelectionChanged: (SectionId?) -> Unit,
    onCreateTitleChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StructureDropdown(
        label = "Раздел",
        createPlaceholder = "Создать раздел",
        options = items.map { StructureOption(id = it.id, title = it.title) },
        selectedId = selectedId,
        createTitle = createTitle,
        onSelectionChanged = onSelectionChanged,
        onCreateTitleChanged = onCreateTitleChanged,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ThemePathSpinner(
    items: List<ThemePathItem>,
    selectedId: ThemeId?,
    createTitle: String,
    onSelectionChanged: (ThemeId?) -> Unit,
    onCreateTitleChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StructureDropdown(
        label = "Тема",
        createPlaceholder = "Создать тему",
        options = items.map { StructureOption(id = it.id, title = it.title) },
        selectedId = selectedId,
        createTitle = createTitle,
        onSelectionChanged = onSelectionChanged,
        onCreateTitleChanged = onCreateTitleChanged,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun LessonPathSpinner(
    items: List<LessonPathItem>,
    selectedId: LessonId?,
    createTitle: String,
    onSelectionChanged: (LessonId?) -> Unit,
    onCreateTitleChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StructureDropdown(
        label = "Урок",
        createPlaceholder = "Создать урок",
        options = items.map { StructureOption(id = it.id, title = it.title) },
        selectedId = selectedId,
        createTitle = createTitle,
        onSelectionChanged = onSelectionChanged,
        onCreateTitleChanged = onCreateTitleChanged,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun <T> StructureDropdown(
    label: String,
    createPlaceholder: String,
    options: List<StructureOption<T>>,
    selectedId: T?,
    createTitle: String,
    onSelectionChanged: (T?) -> Unit,
    onCreateTitleChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = remember { mutableStateOf(false) }
    val isCreating = selectedId == null
    val selectedTitle =
        selectedId
            ?.let { id -> options.firstOrNull { it.id == id }?.title }
            ?: createTitle
    val anchorType =
        if (isCreating) {
            MenuAnchorType.PrimaryEditable
        } else {
            MenuAnchorType.PrimaryNotEditable
        }

    ExposedDropdownMenuBox(
        expanded = expanded.value,
        onExpandedChange = { expanded.value = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedTitle,
            onValueChange = { value ->
                if (isCreating) {
                    onCreateTitleChanged(value)
                }
            },
            readOnly = !isCreating,
            singleLine = true,
            label = { Text(label) },
            placeholder = {
                if (isCreating) {
                    Text(createPlaceholder)
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
            shape = schoolQuizDesignCardShape(),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = schoolQuizDesignDeepSurfaceColor(),
                    unfocusedContainerColor = schoolQuizDesignDeepSurfaceColor(),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = schoolQuizDesignLightBorderColor(),
                    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                    unfocusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                ),
            modifier =
                Modifier
                    .menuAnchor(anchorType)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
        ) {
            DropdownMenuItem(
                text = { Text(CREATE_OPTION_TITLE) },
                onClick = {
                    onSelectionChanged(null)
                    expanded.value = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title) },
                    onClick = {
                        onSelectionChanged(option.id)
                        expanded.value = false
                    },
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun LanguageChipRow(
    languages: List<String>,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        languages.ifEmpty { listOf(selectedLanguage.ifBlank { "ru" }) }.forEach { language ->
            FilterChip(
                selected = selectedLanguage == language,
                onClick = { onLanguageSelected(language) },
                label = { Text(language.uppercase()) },
            )
        }
    }
}

private data class StructureOption<T>(
    val id: T,
    val title: String,
)

private const val CREATE_OPTION_TITLE = "Создать"

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun QuestCreateScreenPreview() {
    SchoolQuizTheme {
        QuestCreateContent(
            state =
                QuestCreateUiState(
                    catalogs =
                        listOf(
                            CatalogDisplayItem(
                                id = CatalogId("catalog-1"),
                                name = "Криптография",
                                pictureUrl = null,
                            ),
                        ),
                    selectedCatalogId = CatalogId("catalog-1"),
                    isWaitingForUser = false,
                ),
            onBackClick = {},
            onCatalogSelected = {},
            onQuestSelected = {},
            onSectionSelected = {},
            onThemeSelected = {},
            onLessonSelected = {},
            onQuestTitleChanged = {},
            onSectionTitleChanged = {},
            onThemeTitleChanged = {},
            onLessonTitleChanged = {},
            onLanguageChanged = {},
            onStructureCheckClick = {},
            onContinueDraftClick = {},
            onSubmitToArenaClick = {},
            onQuestionsClick = {},
        )
    }
}
