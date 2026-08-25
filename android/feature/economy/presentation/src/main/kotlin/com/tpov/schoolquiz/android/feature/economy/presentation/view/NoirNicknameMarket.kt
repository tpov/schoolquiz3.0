@file:Suppress("MagicNumber", "FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.economy.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassFill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeLg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeMd
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapePill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.economy.presentation.component.NicknameListingSort
import com.tpov.schoolquiz.android.feature.economy.presentation.component.NicknameMarketTab
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
 * The NFT tab: two shelves, names and logos, and one search box over both.
 *
 * Rows, not cards. A name carries itself and the price says the rest — shorter names cost more, so
 * no rarity label has to be printed, and the only quiet line under a name is who holds it.
 *
 * Search doubles as the availability check: type something nobody holds and the market answers
 * with a row offering to take it. Taken names stay on screen, dimmed, with their holder's tag —
 * hiding them would make a busy market look deserted.
 *
 * Spending gold always asks twice. The price is the button, and only the second tap moves
 * anything: a mis-tap on a lot should not cost a name.
 */
@Composable
fun NoirNicknameMarket(
    state: ShopViewState,
    onEvent: (ShopViewEvent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val nicknames = state.nicknames
    val query = nicknames.listingQuery

    // One field feeds both jobs, so what is typed is also what gets checked for availability.
    LaunchedEffect(query) {
        val candidate = query.trim()
        if (candidate.isEmpty()) return@LaunchedEffect
        delay(AVAILABILITY_DEBOUNCE_MS)
        onEvent(ShopViewEvent.CheckNicknameAvailability(candidate))
    }

    Column(modifier.fillMaxSize()) {
        MarketTabs(state = nicknames, onPick = { onEvent(ShopViewEvent.MarketTabPicked(it)) })

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = contentPadding,
        ) {
            if (nicknames.marketTab == NicknameMarketTab.LOGOS) {
                logoShelf(state = state, onEvent = onEvent)
                return@LazyColumn
            }

            item {
                MarketSearch(
                    query = query,
                    onQuery = { onEvent(ShopViewEvent.ListingQueryChanged(it)) },
                )
            }

            item {
                OwnedPanel(
                    state = nicknames,
                    onWear = { onEvent(ShopViewEvent.SetActiveNickname(it)) },
                    onList = { name, price -> onEvent(ShopViewEvent.ListNicknameForSale(name, price)) },
                    onCancel = { onEvent(ShopViewEvent.CancelNicknameListing(it)) },
                )
            }

            searchAnswer(state = nicknames, query = query, onEvent = onEvent)

            if (nicknames.listings.isNotEmpty()) {
                item {
                    ListingControls(
                        state = nicknames,
                        onSort = { onEvent(ShopViewEvent.ListingSortPicked(it)) },
                    )
                }
            }

            if (nicknames.visibleListings.isEmpty()) {
                item {
                    MarketNote(
                        when {
                            nicknames.isLoading -> "Загрузка…"
                            nicknames.listings.isEmpty() -> "Никто ничего не продаёт"
                            else -> "Ничего не найдено"
                        },
                    )
                }
            } else {
                items(nicknames.visibleListings, key = { "listing-${it.nickname}" }) { listing ->
                    val key = "name:${listing.nickname}"
                    ListingRow(
                        listing = listing,
                        busy = nicknames.processingNickname == listing.nickname,
                        affordable = state.balance.gold >= listing.price,
                        own = nicknames.owned.any { it.nickname == listing.nickname },
                        armed = nicknames.armed == key,
                        onArm = { onEvent(ShopViewEvent.ArmPurchase(if (nicknames.armed == key) null else key)) },
                        onBuy = { onEvent(ShopViewEvent.BuyNickname(listing.nickname)) },
                    )
                }
            }
        }
    }
}

/**
 * What the search box has to say about what was typed.
 *
 * Sits where the results would start, because it is the answer to the question: nobody holds this
 * and here is the price, or somebody does and here is who.
 */
private fun LazyListScope.searchAnswer(
    state: NicknameShopState,
    query: String,
    onEvent: (ShopViewEvent) -> Unit,
) {
    if (query.isBlank()) return
    val verdict = state.draftAvailability
    if (verdict == null) {
        if (state.isCheckingAvailability) item { MarketNote("Проверяем…") }
        return
    }
    val holder = verdict.holder
    val reason = verdict.reason
    when {
        verdict.available ->
            item {
                MarketRow(
                    name = verdict.nickname,
                    meta = if (verdict.price == 0L) "свободно · первое имя бесплатно" else "свободно",
                    accentName = true,
                ) {
                    if (verdict.price > 0) GoldAmount(verdict.price)
                    MarketAction(
                        "Занять",
                        enabled = state.processingNickname != verdict.nickname,
                    ) { onEvent(ShopViewEvent.ClaimNickname(verdict.nickname)) }
                }
            }
        holder != null -> item { MarketRow(name = verdict.nickname, meta = "у $holder", dim = true) {} }
        reason != null -> item { MarketNote(reason.wording()) }
        else -> Unit
    }
}

/**
 * The two shelves, as words rather than buttons.
 *
 * A tab bar with a rule under it, the way the rest of the app writes one — pills here would read
 * as two more things to buy on a screen that is already a list of things to buy.
 */
@Composable
private fun MarketTabs(
    state: NicknameShopState,
    onPick: (NicknameMarketTab) -> Unit,
) {
    val onNames = state.marketTab == NicknameMarketTab.NAMES
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = NoirHair,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarketTab("Имена", onNames) { onPick(NicknameMarketTab.NAMES) }
        MarketTab("Логотипы", !onNames) { onPick(NicknameMarketTab.LOGOS) }
        Spacer(Modifier.weight(1f))
        Text(
            text =
                if (onNames) {
                    "${state.listings.size} в продаже"
                } else {
                    "${state.logos.count { it.owned }} из ${state.logos.size}"
                },
            style = NoirType.kicker.copy(color = NoirTOff),
        )
    }
}

/** The chosen shelf is white and underlined; the other is quiet. */
@Composable
private fun MarketTab(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalNoirAccent.current
    Text(
        text = label,
        style = NoirType.rowTitle.copy(color = if (active) NoirT1 else NoirTOff),
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .drawBehind {
                    if (!active) return@drawBehind
                    drawLine(
                        color = accent,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 4f,
                    )
                }
                .padding(end = 18.dp, top = 14.dp, bottom = 13.dp),
    )
}

/** One box for both jobs: it searches the window and asks whether a name is free. */
@Composable
private fun MarketSearch(
    query: String,
    onQuery: (String) -> Unit,
) {
    val accent = LocalNoirAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(44.dp)
            .clip(NoirShapeMd)
            .background(NoirGlassFill)
            .border(1.dp, NoirGlassStroke, NoirShapeMd)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(NoirIcons.Search, contentDescription = null, tint = NoirTOff, modifier = Modifier.size(15.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = NoirType.rowTitle.copy(fontSize = 15.sp, color = NoirT1),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) {
                Text("Найти имя", style = NoirType.rowTitle.copy(fontSize = 15.sp, color = NoirTOff))
            }
        }
        if (query.isNotEmpty()) {
            Icon(
                NoirIcons.Close,
                contentDescription = "Очистить",
                tint = NoirTOff,
                modifier =
                    Modifier
                        .clip(NoirShapePill)
                        .clickable { onQuery("") }
                        .padding(10.dp)
                        .size(14.dp),
            )
        }
    }
}

/**
 * The names this account holds, kept inside a frame.
 *
 * Bounded rather than loose in the list, because these are the rows that belong to the reader; the
 * market below is everybody else's, and without the frame the two run together.
 */
@Composable
private fun OwnedPanel(
    state: NicknameShopState,
    onWear: (String) -> Unit,
    onList: (String, Long) -> Unit,
    onCancel: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(NoirShapeLg)
            .background(NoirGlassFill)
            .border(1.dp, NoirGlassStroke, NoirShapeLg)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 3.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ВАШИ ИМЕНА", style = NoirType.kicker.copy(color = NoirTOff))
            Spacer(Modifier.weight(1f))
            Text(
                text = "${state.owned.count { it.isForSale }} / ${state.owned.size}",
                style = NoirType.kicker.copy(color = NoirTOff),
            )
        }
        if (state.owned.isEmpty()) {
            Text(
                if (state.isLoading) "Загрузка…" else "Пока ни одного",
                style = NoirType.rowSub.copy(color = NoirTOff),
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            state.ownedWornFirst.forEach { owned ->
                OwnedNicknameRow(
                    owned = owned,
                    busy = state.processingNickname == owned.nickname,
                    onWear = { onWear(owned.nickname) },
                    onList = { price -> onList(owned.nickname, price) },
                    onCancel = { onCancel(owned.nickname) },
                )
            }
        }
    }
}

/**
 * A name you hold.
 *
 * Both things you can do with it are written out. Tapping the name wears it, as the design has it,
 * but a tap target nothing names is a tap target nobody finds — so "надеть" is said as well.
 */
@Composable
private fun OwnedNicknameRow(
    owned: OwnedNickname,
    busy: Boolean,
    onWear: () -> Unit,
    onList: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    var pricing by remember(owned.nickname) { mutableStateOf(false) }
    var priceDraft by remember(owned.nickname) { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = NoirHair,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            },
    ) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .then(if (!owned.active && !busy) Modifier.clickable(onClick = onWear) else Modifier)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    owned.nickname,
                    style = NoirType.rowTitle.copy(color = if (owned.active) NoirT1 else NoirT2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta =
                    when {
                        owned.active -> "надето" to LocalNoirAccent.current
                        owned.isForSale -> "продаётся за ${owned.listedPrice}" to NoirSuccess
                        else -> null
                    }
                if (meta != null) {
                    Text(meta.first, style = NoirType.rowSub.copy(color = meta.second))
                }
            }
            when {
                // Taking a lot down is offered even for the worn name: wearing one cancels its lot
                // now, but an account that reached that state earlier needs a way out of it.
                owned.isForSale -> MarketAction("Снять", enabled = !busy, muted = true, onClick = onCancel)
                pricing -> Unit
                // Selling the name you wear is refused by the server, so the word is left off
                // rather than shown dead: a control that can never fire is worse than no control.
                owned.active -> Unit
                else -> {
                    MarketAction("Надеть", enabled = !busy, onClick = onWear)
                    MarketAction("Продать", enabled = !busy, muted = true) { pricing = true }
                }
            }
        }

        if (pricing && !owned.isForSale) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .width(96.dp)
                        .clip(NoirShapeMd)
                        .background(NoirS1)
                        .border(1.dp, NoirOutline, NoirShapeMd)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    BasicTextField(
                        value = priceDraft,
                        onValueChange = { priceDraft = it.filter(Char::isDigit).take(9) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = NoirType.num.copy(fontSize = 16.sp, color = NoirGold),
                        cursorBrush = SolidColor(NoirGold),
                    )
                    if (priceDraft.isEmpty()) {
                        Text("1", style = NoirType.num.copy(fontSize = 16.sp, color = NoirTOff))
                    }
                }
                Icon(NoirIcons.GoldStack, contentDescription = null, tint = NoirGold, modifier = Modifier.size(15.dp))
                Spacer(Modifier.weight(1f))
                MarketAction("Отмена", enabled = true, muted = true) { pricing = false }
                val price = priceDraft.toLongOrNull() ?: 0L
                MarketAction("Выставить", enabled = !busy && price >= 1L) {
                    onList(price)
                    pricing = false
                }
            }
        }
    }
}

/** The shared shape of a market row: a name, a quiet line, and whatever can be done with it. */
@Composable
private fun MarketRow(
    name: String,
    meta: String?,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    accentName: Boolean = false,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = name,
                style =
                    NoirType.rowTitle.copy(
                        color =
                            when {
                                dim -> NoirTOff
                                accentName -> LocalNoirAccent.current
                                else -> NoirT1
                            },
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) {
                Text(meta, style = NoirType.rowSub.copy(color = if (dim) NoirOutline else NoirT3))
            }
        }
        trailing()
    }
}

/** Somebody else's lot. The price is the button: tap once to arm it, again to spend. */
@Composable
private fun ListingRow(
    listing: NicknameListing,
    busy: Boolean,
    affordable: Boolean,
    own: Boolean,
    armed: Boolean,
    onArm: () -> Unit,
    onBuy: () -> Unit,
) {
    MarketRow(name = listing.nickname, meta = "продаёт ${listing.sellerNickname}") {
        when {
            own -> Text("ваш лот", style = NoirType.kicker.copy(color = NoirTOff))
            !affordable -> GoldAmount(listing.price, tone = NoirOutline)
            armed -> MarketAction("Купить", enabled = !busy, onClick = onBuy)
            else ->
                Box(Modifier.clip(NoirShapePill).clickable(enabled = !busy, onClick = onArm)) {
                    GoldAmount(listing.price)
                }
        }
    }
}

/** The eight emblems. A tile is the glyph, its name, and either a price or the word "ваш". */
private fun LazyListScope.logoShelf(
    state: ShopViewState,
    onEvent: (ShopViewEvent) -> Unit,
) {
    val nicknames = state.nicknames
    if (nicknames.logos.isEmpty()) {
        item { MarketNote("Загрузка…") }
        return
    }
    items(nicknames.logos, key = { "logo-${it.name}" }) { logo ->
        val key = "logo:${logo.name}"
        val armed = nicknames.armed == key
        val affordable = state.balance.gold >= logo.price
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .drawBehind {
                    drawLine(
                        color = NoirHair,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1f,
                    )
                }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = logoGlyph(logo.name),
                contentDescription = null,
                tint = if (logo.owned) LocalNoirAccent.current else NoirT3,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = logo.name.removeSuffix(" Logo"),
                style = NoirType.rowTitle.copy(color = if (logo.owned) NoirT1 else NoirT2),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                logo.owned -> Text("ваш", style = NoirType.kicker.copy(color = LocalNoirAccent.current))
                !affordable -> GoldAmount(logo.price, tone = NoirOutline)
                armed ->
                    MarketAction(
                        "Купить",
                        enabled = nicknames.processingNickname != logo.name,
                    ) { onEvent(ShopViewEvent.BuyLogo(logo.name)) }
                else ->
                    Box(
                        Modifier.clip(NoirShapePill).clickable { onEvent(ShopViewEvent.ArmPurchase(key)) },
                    ) {
                        GoldAmount(logo.price)
                    }
            }
        }
    }
    item {
        Text(
            "Логотип заменяет рамку аватара везде, где встречается имя — таблицы, результаты, меню. " +
                "Выпавшие из коробок ничего не стоят.",
            style = NoirType.rowSub.copy(color = NoirTOff),
            modifier = Modifier.padding(vertical = 14.dp),
        )
    }
}

/**
 * Which glyph stands for which emblem.
 *
 * By name, because the name is all the server sends — there is no id behind a logo, since a gift
 * box hands over the name itself.
 */
private fun logoGlyph(name: String) =
    when {
        name.startsWith("Golden Crown") -> NoirIcons.Trophy
        name.startsWith("Diamond Star") -> NoirIcons.Star
        name.startsWith("Phoenix Wings") -> NoirIcons.Sun
        name.startsWith("Dragon Scale") -> NoirIcons.Gem
        name.startsWith("Crystal Orb") -> NoirIcons.Globe
        name.startsWith("Thunder Bolt") -> NoirIcons.Bolt
        name.startsWith("Mystic Eye") -> NoirIcons.Eye
        else -> NoirIcons.Lock
    }

private fun NicknameRejection?.wording(): String =
    when (this) {
        NicknameRejection.TOO_SHORT -> "Слишком короткое"
        NicknameRejection.TOO_LONG -> "Слишком длинное"
        NicknameRejection.UNSUPPORTED_CHARACTERS -> "Есть недопустимые символы"
        NicknameRejection.BLOCKED_SYMBOL -> "Есть запрещённый символ"
        NicknameRejection.BLOCKED_WORD -> "Такое имя запрещено"
        NicknameRejection.TAKEN -> "Уже занято"
        NicknameRejection.YOURS -> "Это ваше имя"
        null -> ""
    }

@Composable
private fun MarketAction(
    label: String,
    enabled: Boolean,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = LocalNoirAccent.current
    Text(
        text = label.uppercase(),
        style =
            NoirType.button.copy(
                color =
                    when {
                        !enabled -> NoirTOff
                        muted -> NoirT3
                        else -> accent
                    },
            ),
        modifier =
            Modifier
                .clip(NoirShapePill)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun MarketNote(text: String) {
    Text(
        text,
        style = NoirType.rowSub.copy(color = NoirTOff),
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

/** A price always shows its coin: a bare number here could be gold, nolics or a count. */
@Composable
private fun GoldAmount(
    amount: Long,
    modifier: Modifier = Modifier,
    tone: androidx.compose.ui.graphics.Color = NoirGold,
) {
    Row(
        modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("$amount", style = NoirType.num.copy(fontSize = 16.sp, color = tone))
        Icon(NoirIcons.GoldStack, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp))
    }
}

/**
 * The three ways to order the window.
 *
 * Tapping the chosen order again reverses it, rather than each order carrying its own arrow: with
 * three columns that would be six controls for what is really two decisions.
 */
@Composable
private fun ListingControls(
    state: NicknameShopState,
    onSort: (NicknameListingSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortLabel("А–Я", NicknameListingSort.NAME, state, onSort)
        SortLabel("Цена", NicknameListingSort.PRICE, state, onSort)
        SortLabel("Дата", NicknameListingSort.DATE, state, onSort)
    }
}

@Composable
private fun SortLabel(
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
        style = NoirType.kicker.copy(color = if (active) accent else NoirTOff),
        modifier = Modifier.clickable { onSort(sort) }.padding(vertical = 6.dp),
    )
}
