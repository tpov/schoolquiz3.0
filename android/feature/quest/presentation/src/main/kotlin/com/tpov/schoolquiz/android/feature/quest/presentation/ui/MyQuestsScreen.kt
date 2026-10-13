@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.quest.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.R
import com.tpov.schoolquiz.android.core.designsystem.components.StarRating
import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChip
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChipTone
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirFab
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassCard
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirItemTile
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeMd
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirWashHome
import com.tpov.schoolquiz.android.core.designsystem.noir.noirScreenWash
import com.tpov.schoolquiz.android.feature.quest.presentation.DraftQuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.android.feature.quest.presentation.R as QuestR

/**
 * "Мои квесты" screen.
 *
 * The author's shelf, in the same row family as every other list: a category select, draft rows
 * above the published quests, the create FAB in the corner. Drafts carry their return reason —
 * the author must tell an unfinished draft from a rejected submission without leaving the list.
 *
 * isGuest=true → same empty state as 0 quests (no login CTA per spec).
 *
 * Spec: AC#23-29; DFD 3 (02-behavior.md); UX-6 keeps the category selection and the FAB.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun MyQuestsScreen(
    component: MyQuestsComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()

    Box(modifier = modifier.fillMaxSize().noirScreenWash(NoirWashHome)) {
        Column(modifier = Modifier.fillMaxSize()) {
            MyQuestsCategorySelect(
                catalogs = state.catalogs,
                selectedId = state.selectedCatalogId,
                onSelected = component::onCatalogSelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                state.isLoading ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LocalNoirAccent.current)
                    }
                state.quests.isEmpty() && state.drafts.isEmpty() ->
                    MyQuestsEmptyContent(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp),
                    )
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.drafts, key = { it.id.value }) { draft ->
                            DraftQuestRow(
                                item = draft,
                                onClick = { component.onDraftClick(draft) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        itemsIndexed(state.quests, key = { _, quest -> quest.id.value }) { index, quest ->
                            MyQuestRow(
                                order = index + 1,
                                item = quest,
                                onClick = { component.onQuestClick(quest) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
            }
        }

        NoirFab(
            icon = NoirIcons.Plus,
            contentDescription = stringResource(QuestR.string.quest_cd_create_quest),
            onClick = component::onCreateQuestClick,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
        )
    }
}

/**
 * The category filter, drawn as the design's select: one quiet field that opens the list, not a
 * Material spinner with its own idea of chrome. "All categories" stays the explicit first choice.
 */
@Composable
private fun MyQuestsCategorySelect(
    catalogs: List<CatalogDisplayItem>,
    selectedId: CatalogId?,
    onSelected: (CatalogId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName =
        catalogs.firstOrNull { it.id == selectedId }?.name
            ?: stringResource(R.string.ds_spinner_all_categories)

    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(NoirShapeMd)
                .background(NoirS2)
                .border(1.dp, NoirOutline, NoirShapeMd)
                .clickable { expanded = true }
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedName,
                style = NoirType.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = NoirIcons.ChevronRight,
                contentDescription = null,
                tint = NoirT2,
                modifier = Modifier.size(16.dp).rotate(90f),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ds_spinner_all_categories), color = NoirT1) },
                onClick = {
                    expanded = false
                    onSelected(null)
                },
            )
            catalogs.forEach { catalog ->
                DropdownMenuItem(
                    text = { Text(catalog.name, color = NoirT2) },
                    onClick = {
                        expanded = false
                        onSelected(catalog.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun MyQuestsEmptyContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(QuestR.string.quest_empty_quests_title),
                style = NoirType.groupTitle,
                color = NoirT1,
            )
            Text(
                text = stringResource(QuestR.string.quest_empty_quests_hint),
                style = NoirType.rowSub,
                color = NoirT2,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * A draft: pencil tile, title, the question count, and the muted «Черновик» chip on the right —
 * with the return reason in danger red when the review sent the work back.
 */
@Composable
private fun DraftQuestRow(
    item: DraftQuestDisplayItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NoirGlassCard(modifier = modifier.clickable(onClick = onClick)) {
        NoirItemTile(icon = NoirIcons.Pencil, tint = NoirT2)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = item.title,
                style = NoirType.rowTitle.copy(fontSize = 14.5.sp),
                color = NoirT1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(QuestR.string.quest_questions_count, item.questionCount),
                style = NoirType.rowSub,
                color = NoirT2,
            )
            // Почему работа вернулась к автору. Причина хранилась с самого начала и доезжала до
            // модели, но не показывалась: автор видел «Черновик» и не мог отличить свою
            // недоделку от отклонённой заявки.
            item.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    text = stringResource(QuestR.string.quest_draft_returned, humanReadableReason(reason)),
                    style = NoirType.rowSub.copy(fontSize = 12.sp),
                    color = NoirDanger,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        NoirChip(text = stringResource(QuestR.string.quest_draft_label), tone = NoirChipTone.Off)
    }
}

/**
 * A published (or submitted) quest of the author: the row the catalog lists use — a numbered
 * accent tile, the title, and the quest's rating figures on the right.
 */
@Composable
private fun MyQuestRow(
    order: Int,
    item: QuestDisplayItem,
    onClick: (QuestId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalNoirAccent.current
    NoirGlassCard(modifier = modifier.clickable { onClick(item.id) }) {
        Box(
            Modifier
                .size(34.dp)
                .clip(NoirShapeMd)
                .background(accent.copy(alpha = 0.09f))
                .border(1.dp, accent.copy(alpha = 0.22f), NoirShapeMd),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "%02d".format(order),
                style = NoirType.num.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = accent,
            )
        }
        Text(
            text = item.title,
            style = NoirType.rowTitle.copy(fontSize = 14.5.sp),
            color = NoirT1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        StarRating(
            rating = item.averageRating,
            size = 16.dp,
            tint = if (item.averageRating == null) NoirTOff else NoirGold,
        )
    }
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
        stringResource(QuestR.string.quest_draft_returned_delivery)
    } else {
        reason
    }

/** Коды транспорта: только заглавные буквы и подчёркивания, без пробелов. */
private val TRANSPORT_CODE = Regex("[A-Z][A-Z_]{2,}")
