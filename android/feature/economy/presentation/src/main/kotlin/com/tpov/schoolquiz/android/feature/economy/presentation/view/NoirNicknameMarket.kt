@file:Suppress("MagicNumber", "FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.economy.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassFill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSectionRule
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeMd
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapePill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.economy.presentation.component.NicknameListingSort
import com.tpov.schoolquiz.android.feature.economy.presentation.component.NicknameShopState
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopViewEvent
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopViewState
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameRejection
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import kotlinx.coroutines.delay

/** How long the typing has to settle before the name goes to the server. */
private const val AVAILABILITY_DEBOUNCE_MS = 450L

/**
 * The NFT tab: names an account holds, and the window where they change hands.
 *
 * Three blocks in the order somebody works through them — take a name, manage the ones you have,
 * browse what others are selling. Every action is a word rather than a button: the shop already
 * spends its emphasis on the store tab, and a column of filled buttons here would compete with it.
 */
@Composable
fun NoirNicknameMarket(
    state: ShopViewState,
    onEvent: (ShopViewEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nicknames = state.nicknames
    val draft = nicknames.draft

    // The check follows the typing rather than every keystroke: a request per letter would spend a
    // callable on text nobody has finished writing.
    LaunchedEffect(draft) {
        delay(AVAILABILITY_DEBOUNCE_MS)
        onEvent(ShopViewEvent.CheckNicknameAvailability(draft))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ClaimNicknameCard(
                state = state,
                onDraftChange = { onEvent(ShopViewEvent.NicknameDraftChanged(it)) },
                onClaim = { onEvent(ShopViewEvent.ClaimNickname(draft.trim())) },
            )
        }

        item { NoirSectionRule(label = "Мои имена", trailing = "${nicknames.owned.size}") }

        if (nicknames.owned.isEmpty()) {
            item { EmptyNote(if (nicknames.isLoading) "Загрузка…" else "Пока ни одного") }
        } else {
            // Keys are namespaced per section: a listed name appears in both lists at once, and one
            // LazyColumn cannot hold the same key twice.
            items(nicknames.owned, key = { "owned-${it.nickname}" }) { owned ->
                OwnedNicknameRow(
                    owned = owned,
                    busy = nicknames.processingNickname == owned.nickname,
                    onSetActive = { onEvent(ShopViewEvent.SetActiveNickname(owned.nickname)) },
                    onList = { price -> onEvent(ShopViewEvent.ListNicknameForSale(owned.nickname, price)) },
                    onCancel = { onEvent(ShopViewEvent.CancelNicknameListing(owned.nickname)) },
                )
            }
        }

        item {
            NoirSectionRule(
                label = "Витрина",
                trailing = "${nicknames.visibleListings.size} / ${nicknames.listings.size}",
            )
        }

        item {
            ListingControls(
                state = nicknames,
                onQuery = { onEvent(ShopViewEvent.ListingQueryChanged(it)) },
                onSort = { onEvent(ShopViewEvent.ListingSortPicked(it)) },
            )
        }

        if (nicknames.visibleListings.isEmpty()) {
            item {
                EmptyNote(
                    when {
                        nicknames.isLoading -> "Загрузка…"
                        nicknames.listings.isEmpty() -> "Никто ничего не продаёт"
                        else -> "Ничего не найдено"
                    },
                )
            }
        } else {
            items(nicknames.visibleListings, key = { "listing-${it.nickname}" }) { listing ->
                ListingRow(
                    listing = listing,
                    busy = nicknames.processingNickname == listing.nickname,
                    affordable = state.balance.gold >= listing.price,
                    // Your own lot is worth seeing on the shelf, but buying it back is not a
                    // trade — the server refuses it, and offering the tap only earns an error.
                    own = nicknames.owned.any { it.nickname == listing.nickname },
                    onBuy = { onEvent(ShopViewEvent.BuyNickname(listing.nickname)) },
                )
            }
        }
    }
}

// ─── Claim ──────────────────────────────────────────────────────────────────

@Composable
private fun ClaimNicknameCard(
    state: ShopViewState,
    onDraftChange: (String) -> Unit,
    onClaim: () -> Unit,
) {
    val nicknames = state.nicknames
    val verdict = nicknames.draftAvailability
    val accent = LocalNoirAccent.current
    NicknamePanel {
        Text("СОЗДАТЬ ИМЯ", style = NoirType.kicker.copy(fontSize = 9.sp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicTextField(
                value = nicknames.draft,
                onValueChange = onDraftChange,
                singleLine = true,
                textStyle = NoirType.rowTitle,
                cursorBrush = SolidColor(accent),
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(NoirShapeMd)
                        .background(NoirS1)
                        .border(1.dp, NoirOutline, NoirShapeMd)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
            )
            if (verdict?.available == true) {
                // Free is a price too, and the silence where a number should be reads as a screen
                // that has not finished loading.
                if (verdict.price > 0) {
                    GoldAmount(verdict.price)
                } else {
                    Text("БЕСПЛАТНО", style = NoirType.kicker.copy(fontSize = 9.sp, color = NoirSuccess))
                }
            }
            NicknameAction(
                label = "Создать",
                enabled = state.nicknames.canClaimDraft,
                onClick = onClaim,
            )
        }
        // The verdict sits under the field and always occupies a line, so the layout does not jump
        // as answers arrive and are replaced.
        val (note, tone) =
            when {
                nicknames.draft.isBlank() -> null to NoirT3
                nicknames.isCheckingAvailability -> "Проверяем…" to NoirTOff
                nicknames.availabilityUnreachable -> "Не удалось проверить — нет связи с сервером" to NoirDanger
                verdict == null -> "Проверяем…" to NoirTOff
                verdict.available -> "Свободно" to NoirSuccess
                else -> verdict.reason.wording() to NoirDanger
            }
        if (note != null) {
            Text(note, style = NoirType.rowSub.copy(fontSize = 11.sp, color = tone))
        }
    }
}

/** Codes come from the server; the wording is ours, so it can change without a deploy. */
private fun NicknameRejection?.wording(): String =
    when (this) {
        NicknameRejection.TOO_SHORT -> "Слишком коротко"
        NicknameRejection.TOO_LONG -> "Слишком длинно"
        NicknameRejection.UNSUPPORTED_CHARACTERS -> "Недопустимые символы"
        NicknameRejection.BLOCKED_SYMBOL -> "Такой символ нельзя"
        NicknameRejection.BLOCKED_WORD -> "Такое слово нельзя"
        NicknameRejection.TAKEN -> "Уже занято"
        NicknameRejection.YOURS -> "Это имя уже ваше"
        null -> "Нельзя занять"
    }

// ─── Owned ──────────────────────────────────────────────────────────────────

@Composable
private fun OwnedNicknameRow(
    owned: OwnedNickname,
    busy: Boolean,
    onSetActive: () -> Unit,
    onList: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    var priceDraft by remember(owned.nickname) { mutableStateOf("") }
    var pricing by remember(owned.nickname) { mutableStateOf(false) }
    val accent = LocalNoirAccent.current

    NicknamePanel {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    owned.nickname,
                    style = NoirType.rowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Only states worth acting on get a line, and a name in neither state gets none:
                // how it was obtained changes nothing a person can do with it, and an empty caption
                // still takes up a row.
                val state =
                    when {
                        owned.active -> "АКТИВНОЕ" to accent
                        owned.isForSale -> "ПРОДАЁТСЯ ЗА ${owned.listedPrice}" to NoirGold
                        else -> null
                    }
                if (state != null) {
                    Text(state.first, style = NoirType.kicker.copy(fontSize = 9.sp, color = state.second))
                }
            }
            if (owned.active) {
                Icon(NoirIcons.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            } else {
                NicknameAction("Надеть", enabled = !busy, onClick = onSetActive)
            }
        }

        when {
            // Taking a lot down is always offered, even for the name being worn. Wearing one now
            // cancels its lot, but accounts that reached that state earlier would otherwise have
            // no way out of it: the name is on sale, and the only control that could stop it was
            // hidden precisely because the name is active.
            owned.isForSale -> NicknameAction("Снять с продажи", enabled = !busy, muted = true, onClick = onCancel)
            // Selling the name you wear is refused by the server, and offering it here would only
            // produce an error the person could have been spared.
            owned.active -> Unit
            pricing ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicTextField(
                        value = priceDraft,
                        onValueChange = { input -> priceDraft = input.filter { it.isDigit() }.take(9) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = NoirType.num.copy(fontSize = 14.sp, color = NoirT1),
                        cursorBrush = SolidColor(accent),
                        modifier =
                            Modifier
                                .width(120.dp)
                                .clip(NoirShapeMd)
                                .background(NoirS1)
                                .border(1.dp, NoirOutline, NoirShapeMd)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                    Icon(
                        NoirIcons.GoldStack,
                        contentDescription = "золота",
                        tint = NoirGold,
                        modifier = Modifier.size(15.dp),
                    )
                    Box(Modifier.weight(1f))
                    NicknameAction(
                        label = "Выставить",
                        enabled = !busy && (priceDraft.toLongOrNull() ?: 0L) > 0L,
                        onClick = {
                            priceDraft.toLongOrNull()?.takeIf { it > 0L }?.let(onList)
                            pricing = false
                        },
                    )
                }

            else -> NicknameAction("Продать", enabled = !busy, muted = true, onClick = { pricing = true })
        }
    }
}

// ─── Listings ───────────────────────────────────────────────────────────────

@Composable
private fun ListingRow(
    listing: NicknameListing,
    busy: Boolean,
    affordable: Boolean,
    own: Boolean,
    onBuy: () -> Unit,
) {
    NicknamePanel {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    listing.nickname,
                    style = NoirType.rowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "ПРОДАЁТ ${listing.sellerNickname.uppercase()}",
                    style = NoirType.kicker.copy(fontSize = 9.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GoldAmount(listing.price)
            // Priced out rather than hidden: seeing what a name costs is the point of a window,
            // and the price is worth knowing before the account can act on it.
            if (own) {
                Text("ВАШ ЛОТ", style = NoirType.kicker.copy(fontSize = 9.sp, color = NoirTOff))
            } else {
                NicknameAction("Купить", enabled = !busy && affordable, onClick = onBuy)
            }
        }
    }
}

// ─── Shell ──────────────────────────────────────────────────────────────────

@Composable
private fun NicknameAction(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = label.uppercase(),
        style =
            NoirType.kicker.copy(
                fontSize = 10.sp,
                color =
                    when {
                        !enabled -> NoirTOff
                        muted -> NoirT3
                        else -> LocalNoirAccent.current
                    },
            ),
        modifier =
            modifier
                .clip(NoirShapeMd)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 13.dp),
    )
}

@Composable
private fun NicknamePanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NoirShapeMd)
            .background(NoirGlassStroke.copy(alpha = 0.04f))
            .border(1.dp, NoirHair, NoirShapeMd)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = NoirType.rowSub.copy(fontSize = 12.sp, color = NoirTOff),
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    )
}

/** A price always shows its coin: a bare number on this screen could be gold, nolics or a count. */
@Composable
private fun GoldAmount(
    amount: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("$amount", style = NoirType.num.copy(fontSize = 14.sp, color = NoirGold))
        Icon(
            NoirIcons.GoldStack,
            contentDescription = null,
            tint = NoirGold,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Search over the window, and the three ways to order it.
 *
 * Tapping the chosen order again reverses it, rather than each order carrying its own arrow: with
 * three columns that would be six controls for what is really two decisions.
 */
@Composable
private fun ListingControls(
    state: NicknameShopState,
    onQuery: (String) -> Unit,
    onSort: (NicknameListingSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalNoirAccent.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(NoirShapeMd)
                .background(NoirS1)
                .border(1.dp, NoirOutline, NoirShapeMd)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = state.listingQuery,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = NoirType.rowSub.copy(fontSize = 13.sp, color = NoirT1),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.listingQuery.isEmpty()) {
                Text("Поиск по имени", style = NoirType.rowSub.copy(fontSize = 13.sp, color = NoirTOff))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SortChip("А–Я", NicknameListingSort.NAME, state, onSort)
            SortChip("Цена", NicknameListingSort.PRICE, state, onSort)
            SortChip("Дата", NicknameListingSort.DATE, state, onSort)
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    sort: NicknameListingSort,
    state: NicknameShopState,
    onSort: (NicknameListingSort) -> Unit,
) {
    val accent = LocalNoirAccent.current
    val active = state.listingSort == sort
    // The arrow appears only on the chosen column: on the others it would promise a direction
    // nothing is currently sorted by.
    val arrow =
        if (!active) {
            ""
        } else if (state.listingDescending) {
            " ↓"
        } else {
            " ↑"
        }
    Text(
        text = label.uppercase() + arrow,
        style = NoirType.kicker.copy(fontSize = 9.sp, color = if (active) accent else NoirT3),
        modifier =
            Modifier
                .clip(NoirShapePill)
                .background(if (active) accent.copy(alpha = 0.10f) else NoirGlassFill)
                .border(1.dp, if (active) accent.copy(alpha = 0.30f) else NoirHair, NoirShapePill)
                .clickable { onSort(sort) }
                .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}
