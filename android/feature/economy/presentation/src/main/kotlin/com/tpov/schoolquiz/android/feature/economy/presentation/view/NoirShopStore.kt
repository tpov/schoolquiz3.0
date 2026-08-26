@file:Suppress("MagicNumber", "FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.economy.presentation.view

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChip
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChipTone
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassCard
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirItemTile
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSectionRule
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.economy.presentation.R
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopViewState
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCatalogItem
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCurrency
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetShopCatalogUseCase

/**
 * The store shelf.
 *
 * Two shelves, not one list: what the code can actually sell, and below it what is drawn but not
 * wired. The second is shown rather than hidden so the shape of the app is visible, and it is
 * locked rather than merely greyed — a disabled button invites tapping, a lock does not.
 *
 * Every word on a card comes from this module's resources, keyed by [ShopItemId]; the catalogue
 * itself carries data only.
 */
@Composable
internal fun NoirShopStore(
    state: ShopViewState,
    onPurchase: (ShopItemId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.items.sortedBy { DISPLAY_ORDER.indexOf(it.id) }, key = { it.id.wireName }) { item ->
            ShopOfferCard(
                item = item,
                subtitle = item.stateLine(state),
                processing = state.processingItemId == item.id,
                onAction = { onPurchase(item.id) },
            )
        }
        if (BETA_ITEMS.isNotEmpty()) {
            item {
                NoirSectionRule(
                    label = stringResource(R.string.shop_section_in_beta),
                    trailing = BETA_ITEMS.size.toString(),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            items(BETA_ITEMS, key = { it.titleRes }) { beta -> BetaRow(beta) }
        }
    }
}

@Composable
private fun ShopOfferCard(
    item: ShopCatalogItem,
    subtitle: String?,
    processing: Boolean,
    onAction: () -> Unit,
) {
    NoirGlassCard(onClick = if (item.isAvailable && !processing) onAction else null) {
        NoirItemTile(icon = item.icon, tint = item.tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(item.titleRes()),
                style = NoirType.rowTitle.copy(fontSize = 14.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = NoirType.kicker.copy(fontSize = 10.sp, color = NoirTOff),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.defaultMinSize(minHeight = 44.dp),
        ) {
            PriceLabel(item)
            if (item.isAvailable || processing) {
                Text(
                    // Never a chevron: three different outcomes deserve three different words, and
                    // a chevron only ever promises "there is more".
                    text = if (processing) "…" else stringResource(item.actionLabelRes()),
                    style = NoirType.button.copy(fontSize = 12.sp, color = NoirGold),
                )
            } else {
                // Offering "Buy" on something that cannot be bought is worse than offering nothing:
                // the reason is already on the line above.
                Icon(
                    NoirIcons.Lock,
                    contentDescription = stringResource(R.string.shop_cd_locked),
                    tint = NoirTOff,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PriceLabel(item: ShopCatalogItem) {
    val price = item.price ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = price.shortLabel(),
            style = NoirType.num.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        )
        price.currencyIcon?.let { icon ->
            Icon(icon, contentDescription = null, tint = price.currencyTint, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun BetaRow(beta: BetaItem) {
    // 62% of full brightness, per the design: present, legible, plainly not yet yours.
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NoirItemTile(icon = beta.icon, tint = NoirT1.copy(alpha = 0.38f))
        Text(
            text = stringResource(beta.titleRes),
            style = NoirType.rowTitle.copy(color = NoirT1.copy(alpha = 0.62f)),
            modifier = Modifier.weight(1f),
        )
        NoirChip(text = stringResource(R.string.shop_chip_beta), tone = NoirChipTone.Neutral)
        Icon(
            NoirIcons.Lock,
            contentDescription = stringResource(R.string.shop_cd_locked),
            tint = NoirTOff,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * The order the shelf is read in, which is not the order the catalogue is built in.
 *
 * Spending currency comes first, then earning it, then the two that leave the shop entirely.
 */
private val DISPLAY_ORDER =
    listOf(
        ShopItemId.STANDARD_HEART_SLOT,
        ShopItemId.GOLD_HEART,
        ShopItemId.QUIZ_SLOT,
        ShopItemId.AD_REWARD_BOX,
        ShopItemId.DONATE_GOOGLE_PLAY,
        ShopItemId.REFERRAL_PROGRAM,
        ShopItemId.NICKNAME_MARKET,
    )

private data class BetaItem(val titleRes: Int, val icon: ImageVector)

/** Drawn but not wired. Listed here rather than in the catalogue, which only holds what works. */
private val BETA_ITEMS =
    listOf(
        BetaItem(R.string.shop_beta_tournaments, NoirIcons.Calendar),
        BetaItem(R.string.shop_beta_minigames, NoirIcons.Play),
        BetaItem(R.string.shop_beta_quest_builder, NoirIcons.Plus),
    )

private val ShopCatalogItem.icon: ImageVector
    get() =
        when (id) {
            ShopItemId.STANDARD_HEART_SLOT -> NoirIcons.Heart
            ShopItemId.GOLD_HEART -> NoirIcons.Gem
            ShopItemId.QUIZ_SLOT -> NoirIcons.Plus
            ShopItemId.AD_REWARD_BOX -> NoirIcons.Box
            ShopItemId.DONATE_GOOGLE_PLAY -> NoirIcons.Heart
            ShopItemId.REFERRAL_PROGRAM -> NoirIcons.Users
            ShopItemId.NICKNAME_MARKET -> NoirIcons.Gem
        }

/**
 * Tints carry role, not decoration.
 *
 * Only the two lives are coloured — red for the ordinary one, gold for the premium. Everything else
 * stays neutral, because the screen already spends its one accent on the actions.
 */
private val ShopCatalogItem.tint: Color
    get() =
        when (id) {
            ShopItemId.STANDARD_HEART_SLOT -> NoirDanger
            ShopItemId.GOLD_HEART -> NoirGold
            else -> NoirT1.copy(alpha = 0.55f)
        }

private fun ShopCatalogItem.titleRes(): Int =
    when (id) {
        ShopItemId.STANDARD_HEART_SLOT -> R.string.shop_item_standard_heart
        ShopItemId.GOLD_HEART -> R.string.shop_item_gold_heart
        ShopItemId.QUIZ_SLOT -> R.string.shop_item_quiz_slot
        ShopItemId.AD_REWARD_BOX -> R.string.shop_item_ad_reward_box
        ShopItemId.DONATE_GOOGLE_PLAY -> R.string.shop_item_donate_google_play
        ShopItemId.REFERRAL_PROGRAM -> R.string.shop_item_referral_program
        ShopItemId.NICKNAME_MARKET -> R.string.shop_item_nickname_market
    }

private fun ShopCatalogItem.actionLabelRes(): Int =
    when (id) {
        ShopItemId.AD_REWARD_BOX -> R.string.shop_action_watch_ad
        ShopItemId.DONATE_GOOGLE_PLAY -> R.string.shop_action_donate
        ShopItemId.REFERRAL_PROGRAM, ShopItemId.NICKNAME_MARKET -> R.string.shop_action_open
        else -> R.string.shop_action_buy
    }

/**
 * The line under the title: what you hold, and what it will cost next time.
 *
 * Never repeats the price shown alongside it — the next price is a different number, and repeating
 * the same one twice on a card is how a screen starts to look busy without saying more.
 */
@Composable
private fun ShopCatalogItem.stateLine(state: ShopViewState): String? =
    when (id) {
        ShopItemId.STANDARD_HEART_SLOT -> {
            val base = stringResource(R.string.shop_state_you_have, state.balance.standardHearts)
            val maxed = state.balance.standardHearts >= EconomyResourceBalance.MaxStandardHearts
            if (maxed) {
                base + stringResource(R.string.shop_state_maxed_suffix)
            } else {
                // The card's price is this purchase; the line under the title answers the next one,
                // from the same ladder of costs — so a card never prints one number twice.
                base +
                    stringResource(
                        R.string.shop_state_next_suffix,
                        GetShopCatalogUseCase
                            .standardHeartCost(state.balance.standardHearts + 1)
                            .groupedByThousands(),
                    )
            }
        }
        ShopItemId.GOLD_HEART -> stringResource(R.string.shop_state_gold_hearts, state.balance.goldHearts)
        ShopItemId.QUIZ_SLOT -> stringResource(R.string.shop_lock_quiz_slot)
        ShopItemId.AD_REWARD_BOX -> stringResource(R.string.shop_lock_ad_box)
        ShopItemId.DONATE_GOOGLE_PLAY -> stringResource(R.string.shop_lock_donate)
        else -> null
    }

@Composable
private fun com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopPrice.shortLabel(): String =
    when (currency) {
        ShopCurrency.NOLICS, ShopCurrency.GOLD -> amount.groupedByThousands()
        ShopCurrency.ADS ->
            pluralStringResource(R.plurals.shop_ads_count, amount.toInt(), amount)
        ShopCurrency.EXTERNAL -> stringResource(R.string.shop_price_google_play)
        ShopCurrency.FREE -> stringResource(R.string.shop_price_free)
    }

private val com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopPrice.currencyIcon: ImageVector?
    get() =
        when (currency) {
            ShopCurrency.NOLICS -> NoirIcons.Nolic
            ShopCurrency.GOLD -> NoirIcons.GoldStack
            else -> null
        }

private val com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopPrice.currencyTint: Color
    get() = if (currency == ShopCurrency.GOLD) NoirGold else NoirAccentFallback

/** Thin spaces, so a four-digit price reads at a glance and the digits stay tabular. */
private fun Long.groupedByThousands(): String = toString().reversed().chunked(3).joinToString(" ").reversed()

private val NoirAccentFallback = Color(0xFF0599EF)
