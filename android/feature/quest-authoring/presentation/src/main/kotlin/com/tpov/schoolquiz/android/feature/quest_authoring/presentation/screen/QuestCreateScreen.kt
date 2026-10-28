package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component.QuestCreateComponent
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.LessonPathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.NewLessonProposalSubmissionState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.NewLessonProposalUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestArenaTargetNode
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.LINK_EXPIRY_DAY_PRESETS
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestCreateUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestPathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.SectionPathItem
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.ThemePathItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun QuestCreateScreen(
    component: QuestCreateComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()
    val proposalState by component.newLessonProposalState.collectAsState()
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
            onEditorUndoClick = component::onEditorUndoClick,
            onEditorRedoClick = component::onEditorRedoClick,
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
            onPrivateAttemptLimitChanged = component::onPrivateAttemptLimitChanged,
            onPrivateShowResultChanged = component::onPrivateShowResultChanged,
            onPrivateLinkExpiryToggled = component::onPrivateLinkExpiryToggled,
            onPrivateLinkExpiryDaysChanged = component::onPrivateLinkExpiryDaysChanged,
            onClosePrivateQuizLinkClick = component::onClosePrivateQuizLinkClick,
            onStructureCheckClick = component::onStructureCheckClick,
            onContinueDraftClick = component::onContinueDraftClick,
            onSubmitToArenaClick = component::onSubmitToArenaClick,
            onResubmitOverServerVersionClick = component::onResubmitOverServerVersionClick,
            onSubmitNodeLongClick = component::onSubmitNodeToArenaLongClick,
            onQuestionsClick = component::onQuestionsClick,
            proposalState = proposalState,
            onOpenProposal = component::onNewProposalDialogOpened,
            modifier = modifier,
        )
    }

    // «Предложить автору» (CAP-27, история 4.24): целевой путь в чужом курсе, плата 1000 до
    // отправки (UX-23); подача — общая очередь 4.22 через recordNewLesson.
    if (proposalState.visible) {
        NewLessonProposalDialog(
            state = proposalState,
            onCatalogSelected = component::onProposalTargetCatalogSelected,
            onQuestSelected = component::onProposalTargetQuestSelected,
            onSectionSelected = component::onProposalTargetSectionSelected,
            onThemeSelected = component::onProposalTargetThemeSelected,
            onLessonSelected = component::onProposalLessonSelected,
            onSubmit = component::onProposeLessonToAuthorClick,
            onDismiss = component::onNewProposalDialogDismissed,
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
    onPrivateAttemptLimitChanged: (Int) -> Unit,
    onPrivateShowResultChanged: (Boolean) -> Unit,
    onPrivateLinkExpiryToggled: (Boolean) -> Unit,
    onPrivateLinkExpiryDaysChanged: (Int) -> Unit,
    onClosePrivateQuizLinkClick: () -> Unit,
    onStructureCheckClick: () -> Unit,
    onContinueDraftClick: () -> Unit,
    onSubmitToArenaClick: () -> Unit,
    onResubmitOverServerVersionClick: () -> Unit,
    onSubmitNodeLongClick: (QuestArenaTargetNode) -> Unit,
    onQuestionsClick: (Difficulty) -> Unit,
    /** «Предложить автору» (4.24, CAP-27): состояние диалога и его открытие. */
    proposalState: NewLessonProposalUiState,
    onOpenProposal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isStructureActionEnabled = if (state.hasActiveDraft) state.canContinueDraft else state.canCreate
    val isQuestionActionEnabled = state.canOpenQuestions
    val submitQuestLongClick =
        if (state.hasActiveDraft) {
            { onSubmitNodeLongClick(QuestArenaTargetNode.QUEST) }
        } else {
            null
        }
    val submitSectionLongClick =
        if (state.hasActiveDraft) {
            { onSubmitNodeLongClick(QuestArenaTargetNode.SECTION) }
        } else {
            null
        }
    val submitThemeLongClick =
        if (state.hasActiveDraft) {
            { onSubmitNodeLongClick(QuestArenaTargetNode.THEME) }
        } else {
            null
        }
    val submitLessonLongClick =
        if (state.hasActiveDraft) {
            { onSubmitNodeLongClick(QuestArenaTargetNode.LESSON) }
        } else {
            null
        }

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.qa_cd_back))
            }
            Text(
                text = stringResource(R.string.qa_create_title),
                style = NoirType.groupTitle,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
            )
            if (state.isCreating || state.isSubmittingToArena) {
                CircularProgressIndicator(modifier = Modifier.padding(horizontal = 12.dp))
            } else {
                TextButton(
                    onClick = onStructureCheckClick,
                    enabled = isStructureActionEnabled,
                ) {
                    Text(stringResource(R.string.qa_action_save))
                }
            }
        }

        state.activeDraftTitle?.let { title ->
            AssistChip(
                onClick = onContinueDraftClick,
                label = { Text(stringResource(R.string.qa_active_draft_chip, title)) },
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
                    onLongClick = submitQuestLongClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Приватная категория (AD-29) — плоский HARD-набор: Розділ/Тема/Урок автору не
                // показываются, вместо них — настройки ссылки. Иерархия остаётся у всех
                // остальных категорий без изменений.
                if (!state.isPrivateCategory) {
                    SectionPathSpinner(
                        items = state.sectionItems,
                        selectedId = state.selectedSectionId,
                        createTitle = state.newSectionTitle,
                        onSelectionChanged = onSectionSelected,
                        onCreateTitleChanged = onSectionTitleChanged,
                        onLongClick = submitSectionLongClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ThemePathSpinner(
                        items = state.themeItems,
                        selectedId = state.selectedThemeId,
                        createTitle = state.newThemeTitle,
                        onSelectionChanged = onThemeSelected,
                        onCreateTitleChanged = onThemeTitleChanged,
                        onLongClick = submitThemeLongClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LessonPathSpinner(
                        items = state.lessonItems,
                        selectedId = state.selectedLessonId,
                        createTitle = state.newLessonTitle,
                        onSelectionChanged = onLessonSelected,
                        onCreateTitleChanged = onLessonTitleChanged,
                        onLongClick = submitLessonLongClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (state.isPrivateCategory) {
            PrivateLinkSettingsCard(
                state = state,
                onAttemptLimitChanged = onPrivateAttemptLimitChanged,
                onShowResultChanged = onPrivateShowResultChanged,
                onExpiryToggled = onPrivateLinkExpiryToggled,
                onExpiryDaysChanged = onPrivateLinkExpiryDaysChanged,
                onCloseLinkClick = onClosePrivateQuizLinkClick,
                modifier = Modifier.fillMaxWidth(),
            )
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
                text = message.resolveText(),
                color = NoirDanger,
                style = NoirType.rowSub,
            )
        }
        state.arenaMessage?.let { message ->
            Text(
                text = message.resolveText(),
                color = LocalNoirAccent.current,
                style = NoirType.rowSub,
            )
        }

        if (state.isPrivateCategory) {
            // Плоский HARD-набор (AD-29): один вопросный вход, сложность ровно одна.
            BrandPrimaryButton(
                text =
                    if (state.isCreating) {
                        stringResource(R.string.qa_status_saving)
                    } else {
                        stringResource(R.string.qa_hard_questions)
                    },
                onClick = { onQuestionsClick(Difficulty.HARD) },
                enabled = isQuestionActionEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrandPrimaryButton(
                    text =
                        if (state.isCreating) {
                            stringResource(R.string.qa_status_saving)
                        } else {
                            stringResource(R.string.qa_easy_questions)
                        },
                    onClick = { onQuestionsClick(Difficulty.EASY) },
                    enabled = isQuestionActionEnabled,
                    modifier = Modifier.weight(1f),
                )
                BrandPrimaryButton(
                    text =
                        if (state.isCreating) {
                            stringResource(R.string.qa_status_saving)
                        } else {
                            stringResource(R.string.qa_hard_questions)
                        },
                    onClick = { onQuestionsClick(Difficulty.HARD) },
                    enabled = isQuestionActionEnabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (state.hasActiveDraft) {
            BrandPrimaryButton(
                text =
                    if (state.isSubmittingToArena) {
                        stringResource(R.string.qa_status_submitting)
                    } else {
                        stringResource(R.string.qa_submit_to_arena)
                    },
                onClick = onSubmitToArenaClick,
                enabled = state.canSubmitToArena,
                modifier = Modifier.fillMaxWidth(),
            )
            // Третий вход CAP-27 (4.24, UX-23): свой урок — в чужой курс, решает автор курса.
            BrandPrimaryButton(
                text = stringResource(R.string.qa_proposal_open),
                onClick = onOpenProposal,
                enabled = !state.isSubmittingToArena,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Расхождение показывается словами и одним действием: работа автора цела и лежит в
        // очереди, а решение у него ровно одно — отправить её заново поверх серверной версии
        // (AD-24). Без этого блока конфликт виден только как немой статус черновика.
        //
        // Два разных расхождения — два разных экрана. Названная сервером версия даёт число и
        // кнопку. Неназванная не даёт ни того, ни другого: прежний экран подставлял на её место
        // ноль и предлагал «отправить заново поверх версии 0» — версии, которой не существует, а
        // сама отправка уходила без сверки вообще. Такое расхождение снимает следующий снимок с
        // сервера, и до него автору сообщается положение дел, а не ложное решение.
        state.draftConflict?.let { conflict ->
            val serverVersion = conflict.serverVersion
            if (serverVersion == null) {
                Text(
                    text = stringResource(R.string.qa_conflict_banner_unknown_version),
                    color = NoirDanger,
                    style = NoirType.rowSub,
                )
                Text(
                    text = stringResource(R.string.qa_conflict_unknown_version_hint),
                    style = NoirType.rowSub,
                )
            } else {
                Text(
                    text = stringResource(R.string.qa_conflict_banner, serverVersion),
                    color = NoirDanger,
                    style = NoirType.rowSub,
                )
                BrandPrimaryButton(
                    text = stringResource(R.string.qa_conflict_resubmit, serverVersion),
                    onClick = onResubmitOverServerVersionClick,
                    enabled = state.canResubmitOverServerVersion,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    StructureDropdown(
        label = stringResource(R.string.qa_label_quest),
        createPlaceholder = stringResource(R.string.qa_create_quest),
        options = items.map { StructureOption(id = it.id, title = it.title) },
        selectedId = selectedId,
        createTitle = createTitle,
        onSelectionChanged = onSelectionChanged,
        onCreateTitleChanged = onCreateTitleChanged,
        onLongClick = onLongClick,
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
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    StructureDropdown(
        label = stringResource(R.string.qa_label_section),
        createPlaceholder = stringResource(R.string.qa_create_section),
        options = items.map { StructureOption(id = it.id, title = it.title) },
        selectedId = selectedId,
        createTitle = createTitle,
        onSelectionChanged = onSelectionChanged,
        onCreateTitleChanged = onCreateTitleChanged,
        onLongClick = onLongClick,
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
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    StructureDropdown(
        label = stringResource(R.string.qa_label_theme),
        createPlaceholder = stringResource(R.string.qa_create_theme),
        options = items.map { StructureOption(id = it.id, title = it.title) },
        selectedId = selectedId,
        createTitle = createTitle,
        onSelectionChanged = onSelectionChanged,
        onCreateTitleChanged = onCreateTitleChanged,
        onLongClick = onLongClick,
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
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    StructureDropdown(
        label = stringResource(R.string.qa_label_lesson),
        createPlaceholder = stringResource(R.string.qa_create_lesson),
        options = items.map { StructureOption(id = it.id, title = it.title) },
        selectedId = selectedId,
        createTitle = createTitle,
        onSelectionChanged = onSelectionChanged,
        onCreateTitleChanged = onCreateTitleChanged,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    onLongClick: (() -> Unit)?,
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
                    focusedTextColor = NoirT1,
                    unfocusedTextColor = NoirT1,
                    focusedContainerColor = schoolQuizDesignDeepSurfaceColor(),
                    unfocusedContainerColor = schoolQuizDesignDeepSurfaceColor(),
                    focusedBorderColor = LocalNoirAccent.current,
                    unfocusedBorderColor = schoolQuizDesignLightBorderColor(),
                    focusedTrailingIconColor = LocalNoirAccent.current,
                    unfocusedTrailingIconColor = LocalNoirAccent.current,
                ),
            modifier =
                Modifier
                    .menuAnchor(anchorType)
                    .then(
                        if (onLongClick != null && !isCreating) {
                            Modifier.combinedClickable(
                                onClick = { expanded.value = true },
                                onLongClick = onLongClick,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.qa_action_create)) },
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

/**
 * Настройки приватной ссылки (AD-29): целый лимит попыток, видимость результата и срок.
 *
 * Дефолты — доменные (1 попытка, результат скрыт, «без срока»); карточка показывает их, когда
 * квиз новый, и СОХРАНЁННЫЕ значения, когда загружен существующий. Ручное закрытие ссылки —
 * отдельное решение автора: закрывается навсегда, повторному включению не подлежит (UX-16).
 */
@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
private fun PrivateLinkSettingsCard(
    state: QuestCreateUiState,
    onAttemptLimitChanged: (Int) -> Unit,
    onShowResultChanged: (Boolean) -> Unit,
    onExpiryToggled: (Boolean) -> Unit,
    onExpiryDaysChanged: (Int) -> Unit,
    onCloseLinkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BrandCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.qa_private_link_settings),
                style = NoirType.groupTitle,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.qa_private_attempt_limit),
                    style = NoirType.rowSub,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onAttemptLimitChanged(state.privateAttemptLimit - 1) }) {
                    Text("−", style = NoirType.rowSub)
                }
                Text(
                    text = state.privateAttemptLimit.toString(),
                    style = NoirType.rowSub,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = { onAttemptLimitChanged(state.privateAttemptLimit + 1) }) {
                    Text("+", style = NoirType.rowSub)
                }
            }
            SettingSwitchRow(
                label = stringResource(R.string.qa_private_show_result),
                checked = state.privateShowResult,
                onCheckedChange = onShowResultChanged,
            )
            SettingSwitchRow(
                label = stringResource(R.string.qa_private_link_expiry),
                checked = state.privateLinkHasExpiry,
                onCheckedChange = onExpiryToggled,
            )
            if (state.privateLinkHasExpiry) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LINK_EXPIRY_DAY_PRESETS.forEach { days ->
                        FilterChip(
                            selected = state.privateLinkExpiresInDays == days,
                            onClick = { onExpiryDaysChanged(days) },
                            label = { Text(stringResource(R.string.qa_private_expiry_days, days)) },
                        )
                    }
                }
            }
            if (state.hasActiveDraft) {
                BrandPrimaryButton(
                    text = stringResource(R.string.qa_private_close_link),
                    onClick = onCloseLinkClick,
                    enabled = state.canClosePrivateLink,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.qa_private_close_link_hint),
                    style = NoirType.rowSub,
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = NoirType.rowSub,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Диалог «Предложить автору» (CAP-27, история 4.24, UX-23): целевой путь в чужом курсе —
 * каталог → квест → раздел → тема из опубликованных деревьев; урок черновика на выбор; плата
 * 1000 видна до отправки. Показ платы — не власть: платит приёмка 4.21 из тела подачи.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongMethod")
@Composable
private fun NewLessonProposalDialog(
    state: NewLessonProposalUiState,
    onCatalogSelected: (CatalogId?) -> Unit,
    onQuestSelected: (QuestId?) -> Unit,
    onSectionSelected: (SectionId?) -> Unit,
    onThemeSelected: (ThemeId?) -> Unit,
    onLessonSelected: (DraftLessonId?) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        containerColor = schoolQuizDesignDeepSurfaceColor(),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.qa_proposal_header),
                    style = NoirType.kicker,
                )
                if (state.lessonChoices.size > 1) {
                    Text(
                        text = stringResource(R.string.qa_proposal_lesson),
                        style = NoirType.rowSub.copy(color = NoirT2),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.lessonChoices.forEach { choice ->
                            FilterChip(
                                selected = state.selectedLessonId == choice.id,
                                onClick = { onLessonSelected(choice.id) },
                                label = { Text(choice.title, style = NoirType.rowSub) },
                            )
                        }
                    }
                }
                ProposalTargetDropdown(
                    label = stringResource(R.string.qa_proposal_target_catalog),
                    options = state.catalogs.map { ProposalTargetOption(it.id.value, it.name) },
                    selectedId = state.selectedCatalogId?.value,
                    onSelected = { id -> onCatalogSelected(id?.let(::CatalogId)) },
                )
                ProposalTargetDropdown(
                    label = stringResource(R.string.qa_label_quest),
                    options = state.questItems.map { ProposalTargetOption(it.id.value, it.title) },
                    selectedId = state.selectedQuestId?.value,
                    onSelected = { id -> onQuestSelected(id?.let(::QuestId)) },
                )
                ProposalTargetDropdown(
                    label = stringResource(R.string.qa_label_section),
                    options = state.sectionItems.map { ProposalTargetOption(it.id.value, it.title) },
                    selectedId = state.selectedSectionId?.value,
                    onSelected = { id -> onSectionSelected(id?.let(::SectionId)) },
                )
                ProposalTargetDropdown(
                    label = stringResource(R.string.qa_label_theme),
                    options = state.themeItems.map { ProposalTargetOption(it.id.value, it.title) },
                    selectedId = state.selectedThemeId?.value,
                    onSelected = { id -> onThemeSelected(id?.let(::ThemeId)) },
                )
                Text(
                    text =
                        if (state.feeQuote == null) {
                            stringResource(R.string.qa_proposal_fee_unknown)
                        } else {
                            stringResource(R.string.qa_proposal_fee, state.feeQuote.feeNolics)
                        },
                    style = NoirType.rowSub.copy(color = NoirT2),
                )
                when (state.submission) {
                    NewLessonProposalSubmissionState.Failed ->
                        Text(
                            text = stringResource(R.string.qa_proposal_failed),
                            style = NoirType.rowSub.copy(color = NoirDanger),
                        )
                    NewLessonProposalSubmissionState.Done ->
                        Text(
                            text = stringResource(R.string.qa_proposal_sent),
                            style = NoirType.rowSub.copy(color = NoirSuccess),
                        )
                    NewLessonProposalSubmissionState.Idle -> Unit
                    NewLessonProposalSubmissionState.Sending -> Unit
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.qa_proposal_cancel),
                        style = NoirType.button.copy(color = NoirT3),
                        modifier = Modifier.clickable(onClick = onDismiss),
                    )
                    Text(
                        text =
                            if (state.submission == NewLessonProposalSubmissionState.Sending) {
                                stringResource(R.string.qa_proposal_sending)
                            } else {
                                stringResource(R.string.qa_proposal_send)
                            },
                        style = NoirType.button.copy(
                            color = if (state.targetComplete &&
                                state.submission != NewLessonProposalSubmissionState.Sending
                            ) {
                                LocalNoirAccent.current
                            } else {
                                NoirTOff
                            },
                        ),
                        modifier = Modifier.clickable(
                            enabled = state.targetComplete &&
                                state.submission != NewLessonProposalSubmissionState.Sending,
                            onClick = onSubmit,
                        ),
                    )
                }
            }
        },
    )
}

/** Вариант целевого пути — строка опубликованного дерева. */
private data class ProposalTargetOption(
    val id: String,
    val title: String,
)

/** Селектор только-чтение: чужой курс нельзя создать из диалога — только выбрать существующий. */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ProposalTargetDropdown(
    label: String,
    options: List<ProposalTargetOption>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }
    val selectedTitle = selectedId?.let { id -> options.firstOrNull { it.id == id }?.title }.orEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded.value,
        onExpandedChange = { expanded.value = it },
    ) {
        OutlinedTextField(
            value = selectedTitle,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
            shape = schoolQuizDesignCardShape(),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NoirT1,
                    unfocusedTextColor = NoirT1,
                    focusedContainerColor = schoolQuizDesignDeepSurfaceColor(),
                    unfocusedContainerColor = schoolQuizDesignDeepSurfaceColor(),
                    focusedBorderColor = LocalNoirAccent.current,
                    unfocusedBorderColor = schoolQuizDesignLightBorderColor(),
                ),
            modifier =
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title) },
                    onClick = {
                        onSelected(option.id)
                        expanded.value = false
                    },
                )
            }
        }
    }
}

private data class StructureOption<T>(
    val id: T,
    val title: String,
)

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun questCreateScreenPreview() {
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
            onPrivateAttemptLimitChanged = {},
            onPrivateShowResultChanged = {},
            onPrivateLinkExpiryToggled = {},
            onPrivateLinkExpiryDaysChanged = {},
            onClosePrivateQuizLinkClick = {},
            onStructureCheckClick = {},
            onContinueDraftClick = {},
            onSubmitToArenaClick = {},
            onResubmitOverServerVersionClick = {},
            onSubmitNodeLongClick = {},
            onQuestionsClick = {},
            proposalState = NewLessonProposalUiState(),
            onOpenProposal = {},
        )
    }
}
