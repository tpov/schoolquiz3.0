package com.tpov.schoolquiz.android.feature.quest.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.CatalogSpinner
import com.tpov.schoolquiz.android.core.designsystem.components.QuestCard
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButtonStyle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChip
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDialog
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDialogAction
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirInk
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSkeletonRows
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quest.presentation.DraftQuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.DraftSubmitState
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.R
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestReviewOutcomeSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SUBMITTABLE_DRAFT_STATUSES

/**
 * "Мои квесты" screen.
 *
 * Layout:
 *   - CatalogSpinner (category filter)
 *   - LazyColumn of QuestCards
 *   - Empty state with arrow-to-FAB hint when no quests
 *   - FAB: create quest (navigates to OpenQuestCreate)
 *
 * isGuest=true → same empty state as 0 quests (no login CTA per spec).
 *
 * NOIR (epic 9.2): the screen's own surfaces — draft cards, outcome block, submit dialog, FAB —
 * are built from designsystem/noir primitives and tokens; [QuestCard] and [CatalogSpinner] stay
 * the shared design-system components they are. Strings and component contracts untouched
 * (UX-5/UX-6/UX-14).
 *
 * Spec: AC#23-29; DFD 3 (02-behavior.md)
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun MyQuestsScreen(
    component: MyQuestsComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()
    val submitState by component.submitState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CatalogSpinner(
                items = state.catalogs,
                selectedId = state.selectedCatalogId,
                onSelectionChanged = component::onCatalogSelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.isLoading) {
                // Настоящая загрузка — скелет, не пустота (UX-14): экран показывает, что список
                // будет, а не что его нет.
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    NoirSkeletonRows(modifier = Modifier.fillMaxWidth())
                }
            } else if (state.quests.isEmpty() && state.drafts.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.quest_empty_quests_title),
                            style = NoirType.groupTitle,
                        )
                        Text(
                            text = stringResource(R.string.quest_empty_quests_hint),
                            style = NoirType.rowSub,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.drafts) { draft ->
                        DraftQuestCard(
                            item = draft,
                            review = state.reviewOutcomes[draft.id.value],
                            onClick = { component.onDraftClick(draft) },
                            onSubmitClick = { component.onDraftSubmitClick(draft) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    items(state.quests) { quest ->
                        QuestCard(
                            item = quest,
                            onClick = { component.onQuestClick(quest) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        submitState?.let { current ->
            DraftSubmitDialog(
                state = current,
                onConfirm = component::onDraftSubmitConfirm,
                onRetry = { component.onDraftSubmitClick(current.draft) },
                onDismiss = component::onDraftSubmitDismiss,
            )
        }

        CreateQuestFab(
            contentDescription = stringResource(R.string.quest_cd_create_quest),
            onClick = component::onCreateQuestClick,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
        )
    }
}

/**
 * Круглая акцентная кнопка создания: 56dp (цель ≥44dp с запасом), ink-иконка на акценте —
 * единственная залитая кнопка экрана (NOIR-SPEC §6.1, бюджет акцента).
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun CreateQuestFab(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(LocalNoirAccent.current)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = contentDescription,
            tint = NoirInk,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Кнопка подачи на карточке видна у подающего черновика: статус годен и есть вопросы. */
private fun DraftQuestDisplayItem.isSubmittable(): Boolean = status in SUBMITTABLE_DRAFT_STATUSES && questionCount > 0

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DraftQuestCard(
    item: DraftQuestDisplayItem,
    review: QuestReviewOutcomeSnapshot?,
    onClick: () -> Unit,
    onSubmitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NoirGroup(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = NoirType.rowTitle,
                    modifier = Modifier.weight(1f),
                )
                NoirChip(text = stringResource(R.string.quest_draft_label))
            }
            Text(
                text = stringResource(R.string.quest_questions_count, item.questionCount),
                style = NoirType.rowSub,
                modifier = Modifier.padding(top = 6.dp),
            )
            // Почему работа вернулась к автору. Причина хранилась с самого начала и доезжала до
            // модели, но не показывалась: автор видел «Черновик» и не мог отличить свою
            // недоделку от отклонённой заявки.
            item.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    text = stringResource(R.string.quest_draft_returned, humanReadableReason(reason)),
                    color = NoirDanger,
                    style = NoirType.rowSub,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            // Исход модельной проверки (4.16, UX-6): штрихи проверок, «Проверка задерживается»,
            // «Не прошёл» со списком заваленных вопросов и «Исправить вопросы».
            DraftReviewOutcomeBlock(review = review, onFixClick = onClick)
            // Подача с карточки (история 4.11, D13): раньше карточка умела только открыть
            // редактор, и черновик нельзя было выбрать в форме подачи.
            if (item.isSubmittable()) {
                // Ghost, не primary: залитая кнопка на экране одна — FAB создания (NOIR-SPEC §6.1).
                NoirButton(
                    text = stringResource(R.string.quest_submit_for_review),
                    onClick = onSubmitClick,
                    modifier =
                        Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth(),
                    style = NoirButtonStyle.Ghost,
                )
            }
        }
    }
}

/**
 * Блок исхода проверки на карточке черновика (4.16, UX-6).
 *
 * Ожидание — два штриха проверок («Слова», «Факты»); задержка — «Проверка задерживается» (никогда
 * не отказ по качеству); отказ — «Не прошёл» с числом и расширением по вопросам: урок, этап,
 * причина, затем «Исправить вопросы». Определённый язык — деталь-строка. Публикация тегами
 * места показывается списком квестов и здесь не дублируется.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DraftReviewOutcomeBlock(
    review: QuestReviewOutcomeSnapshot?,
    onFixClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (review == null) return
    Column(modifier = modifier.fillMaxWidth()) {
        review.detectedLanguage?.let { language ->
            Text(
                text = stringResource(R.string.quest_review_language, humanReadableLanguage(language)),
                style = NoirType.rowSub,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        when {
            review.isDelayed -> {
                Text(
                    text = stringResource(R.string.quest_review_delayed),
                    style = NoirType.rowSub,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            review.isWaiting -> {
                Text(
                    text = stringResource(R.string.quest_review_strokes),
                    style = NoirType.rowSub,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            review.isRejected -> {
                Text(
                    text = stringResource(R.string.quest_review_failed_tag, review.failedQuestions.size),
                    color = NoirDanger,
                    style = NoirType.rowSub,
                    modifier = Modifier.padding(top = 6.dp),
                )
                review.failedQuestions.forEach { failure ->
                    Text(
                        text =
                            stringResource(
                                R.string.quest_review_failed_line,
                                failure.lessonId,
                                humanReadableStage(failure.stage),
                                failure.reason,
                            ),
                        style = NoirType.rowSub,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                NoirButton(
                    text = stringResource(R.string.quest_fix_questions),
                    onClick = onFixClick,
                    modifier =
                        Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth(),
                    style = NoirButtonStyle.Ghost,
                )
            }
        }
    }
}

@Composable
private fun humanReadableStage(stage: String): String =
    when (stage) {
        "WORDS" -> stringResource(R.string.quest_stage_words)
        "FACTS" -> stringResource(R.string.quest_stage_facts)
        "TRANSLATE" -> stringResource(R.string.quest_stage_translate)
        else -> stage
    }

@Composable
private fun humanReadableLanguage(language: String): String =
    when (language) {
        "ru" -> stringResource(R.string.quest_language_ru)
        "uk" -> stringResource(R.string.quest_language_uk)
        "en" -> stringResource(R.string.quest_language_en)
        else -> language
    }

/**
 * Диалог подачи: цена названа до отправки (D13), подтверждение без цитаты невозможно.
 *
 * Три состояния цитаты честно разведены: загрузка (спиннер), отказ (текст и «Повторить» —
 * повтор перезапрашивает цену, а не отправляет вслепую) и готовность (плата + «Отправить»).
 * NOIR-оболочка (9.2): NoirDialog, mono-кнопки; строки не менялись.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DraftSubmitDialog(
    state: DraftSubmitState,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NoirDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.quest_submit_for_review),
        modifier = modifier,
        content = {
            Text(
                text = state.draft.title,
                style = NoirType.rowTitle,
            )
            when {
                state.isQuoteLoading -> {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .padding(top = 12.dp)
                                .size(24.dp),
                    )
                }
                state.isQuoteFailed -> {
                    Text(
                        text = stringResource(R.string.quest_submit_quote_failed),
                        color = NoirDanger,
                        style = NoirType.rowSub,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                state.quote != null -> {
                    Text(
                        text = stringResource(R.string.quest_submit_fee_line, state.quote.feeNolics),
                        style = NoirType.rowSub,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (state.isSubmitFailed) {
                Text(
                    text = stringResource(R.string.quest_submit_failed),
                    color = NoirDanger,
                    style = NoirType.rowSub,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        actions = {
            when {
                state.isQuoteFailed -> {
                    NoirDialogAction(stringResource(R.string.quest_submit_retry), onRetry, primary = true)
                    NoirDialogAction(stringResource(R.string.quest_cancel), onDismiss)
                }
                state.quote != null -> {
                    NoirDialogAction(
                        text =
                            if (state.isSubmitting) {
                                stringResource(R.string.quest_submit_sending)
                            } else {
                                stringResource(R.string.quest_submit_confirm)
                            },
                        onClick = onConfirm,
                        primary = true,
                        enabled = !state.isSubmitting,
                    )
                    NoirDialogAction(stringResource(R.string.quest_cancel), onDismiss)
                }
                else -> NoirDialogAction(stringResource(R.string.quest_cancel), onDismiss)
            }
        },
    )
}

/**
 * Причина возврата словами, понятными автору.
 *
 * В поле лежит либо текст рецензента, либо код сбоя доставки (`NOT_FOUND`, `UNAUTHENTICATED` и
 * прочие из [CallableErrors]). Код автору не говорит ничего и выглядит как поломка приложения,
 * поэтому все транспортные коды сводятся к одной честной фразе, а человеческий текст рецензента
 * показывается как есть.
 */
@Composable
private fun humanReadableReason(reason: String): String =
    if (reason.trim().matches(TRANSPORT_CODE)) {
        stringResource(R.string.quest_draft_returned_delivery)
    } else {
        reason
    }

/** Коды транспорта: только заглавные буквы и подчёркивания, без пробелов. */
private val TRANSPORT_CODE = Regex("[A-Z][A-Z_]{2,}")
