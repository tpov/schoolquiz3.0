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
            onSaveQuestionClick = component::onSaveQuestionClick,
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
            onDifficultySelected = component::onDifficultySelected,
            onCreateClick = component::onCreateClick,
            onContinueDraftClick = component::onContinueDraftClick,
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
    onDifficultySelected: (Difficulty) -> Unit,
    onCreateClick: () -> Unit,
    onContinueDraftClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        state.activeDraftTitle?.let { title ->
            AssistChip(
                onClick = {},
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
                    onSelectionChanged = onQuestSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.selectedQuestId == null) {
                    StructureTitleField(
                        value = state.newQuestTitle,
                        onValueChange = onQuestTitleChanged,
                        label = "Название квеста",
                    )
                }
                SectionPathSpinner(
                    items = state.sectionItems,
                    selectedId = state.selectedSectionId,
                    onSelectionChanged = onSectionSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.selectedSectionId == null) {
                    StructureTitleField(
                        value = state.newSectionTitle,
                        onValueChange = onSectionTitleChanged,
                        label = "Название раздела",
                    )
                }
                ThemePathSpinner(
                    items = state.themeItems,
                    selectedId = state.selectedThemeId,
                    onSelectionChanged = onThemeSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.selectedThemeId == null) {
                    StructureTitleField(
                        value = state.newThemeTitle,
                        onValueChange = onThemeTitleChanged,
                        label = "Название темы",
                    )
                }
                LessonPathSpinner(
                    items = state.lessonItems,
                    selectedId = state.selectedLessonId,
                    onSelectionChanged = onLessonSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.selectedLessonId == null) {
                    StructureTitleField(
                        value = state.newLessonTitle,
                        onValueChange = onLessonTitleChanged,
                        label = "Название урока",
                    )
                }
            }
        }

        BrandCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DifficultyChip(
                        title = "Легкий",
                        selected = state.defaultDifficulty == Difficulty.EASY,
                        onClick = { onDifficultySelected(Difficulty.EASY) },
                        modifier = Modifier.weight(1f),
                    )
                    DifficultyChip(
                        title = "Сложный",
                        selected = state.defaultDifficulty == Difficulty.HARD,
                        onClick = { onDifficultySelected(Difficulty.HARD) },
                        modifier = Modifier.weight(1f),
                    )
                }
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

        BrandPrimaryButton(
            text =
                when {
                    state.isCreating -> "Сохраняю"
                    else -> "К вопросам"
                },
            onClick = if (state.hasActiveDraft) onContinueDraftClick else onCreateClick,
            enabled = if (state.hasActiveDraft) state.canContinueDraft else state.canCreate,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isCreating) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun QuestPathSpinner(
    items: List<QuestPathItem>,
    selectedId: QuestId?,
    onSelectionChanged: (QuestId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    StructureDropdown(
        label = "Квест",
        options = items.map { StructureOption(id = it.id, title = it.title) },
        selectedId = selectedId,
        onSelectionChanged = onSelectionChanged,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun SectionPathSpinner(
    items: List<SectionPathItem>,
    selectedId: SectionId?,
    onSelectionChanged: (SectionId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    StructureDropdown(
        label = "Раздел",
        options = items.map { StructureOption(id = it.id, title = it.title) },
        selectedId = selectedId,
        onSelectionChanged = onSelectionChanged,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ThemePathSpinner(
    items: List<ThemePathItem>,
    selectedId: ThemeId?,
    onSelectionChanged: (ThemeId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    StructureDropdown(
        label = "Тема",
        options = items.map { StructureOption(id = it.id, title = it.title) },
        selectedId = selectedId,
        onSelectionChanged = onSelectionChanged,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun LessonPathSpinner(
    items: List<LessonPathItem>,
    selectedId: LessonId?,
    onSelectionChanged: (LessonId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    StructureDropdown(
        label = "Урок",
        options = items.map { StructureOption(id = it.id, title = it.title) },
        selectedId = selectedId,
        onSelectionChanged = onSelectionChanged,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun <T> StructureDropdown(
    label: String,
    options: List<StructureOption<T>>,
    selectedId: T?,
    onSelectionChanged: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = remember { mutableStateOf(false) }
    val selectedTitle =
        selectedId
            ?.let { id -> options.firstOrNull { it.id == id }?.title }
            ?: CREATE_OPTION_TITLE

    ExposedDropdownMenuBox(
        expanded = expanded.value,
        onExpandedChange = { expanded.value = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedTitle,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
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
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
private fun StructureTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = schoolQuizDesignCardShape(),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = schoolQuizDesignDeepSurfaceColor(),
                unfocusedContainerColor = schoolQuizDesignDeepSurfaceColor(),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = schoolQuizDesignLightBorderColor(),
            ),
        modifier = Modifier.fillMaxWidth(),
    )
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

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DifficultyChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier = modifier,
    )
}

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
            onDifficultySelected = {},
            onCreateClick = {},
            onContinueDraftClick = {},
        )
    }
}
