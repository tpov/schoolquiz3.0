@file:Suppress("MagicNumber", "FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.economy.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.economy.presentation.R
import com.tpov.schoolquiz.android.feature.economy.presentation.component.PremiumSurfaceState
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopViewState
import com.tpov.schoolquiz.android.feature.economy.presentation.component.StoreShelfStatus
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyReceipt
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCatalogItem
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCurrency
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.storeProductId
import java.text.DateFormat
import java.util.Date

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
        // Премиальная поверхность (7.3): типы поверх серверных слов, без решений за сервер.
        item { PremiumSurfaceCard(state.premium) }
        items(state.items.sortedBy { DISPLAY_ORDER.indexOf(it.id) }, key = { it.id.wireName }) { item ->
            ShopOfferCard(
                item = item,
                subtitle = item.stateLine(state),
                processing = state.processingItemId == item.id,
                onAction = { onPurchase(item.id) },
            )
        }
        // «За что начислено» (история 5.4): свежие квитанции владельца — причина каждым словом
        // на языке устройства; незнакомый ключ деградирует до честного «начислено N».
        if (state.receipts.isNotEmpty()) {
            item {
                NoirSectionRule(
                    label = stringResource(R.string.receipts_section_title),
                    trailing = state.receipts.size.toString(),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            items(state.receipts, key = { it.id }) { receipt ->
                ReceiptRow(receipt)
            }
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
private fun PremiumSurfaceCard(
    premium: PremiumSurfaceState,
    modifier: Modifier = Modifier,
) {
    // Презентационные часы: активность и «завтра» — слова сервера, пересчитанные на экране.
    // Ничего не выдаётся и не запрещается здесь: сервер перепроверяет обе вещи в момент заявки.
    val nowMs = remember { System.currentTimeMillis() }
    if (!premium.activeAt(nowMs) && premium.dailyGiftAvailableAtMs == 0L) return
    NoirGlassCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.shop_premium_section).uppercase(),
                style = NoirType.kicker.copy(color = NoirGold),
            )
            Spacer(Modifier.height(4.dp))
            if (premium.activeAt(nowMs)) {
                Text(
                    text =
                        stringResource(
                            R.string.shop_premium_active_until,
                            DateFormat.getDateInstance(DateFormat.LONG).format(Date(premium.activeUntilMs)),
                        ),
                    style = NoirType.rowTitle.copy(fontSize = 13.5.sp),
                )
                Text(
                    text =
                        stringResource(
                            if (premium.dailyGiftAvailableAt(nowMs)) {
                                R.string.shop_premium_gift_available
                            } else {
                                R.string.shop_premium_gift_tomorrow
                            },
                        ),
                    style = NoirType.rowSub.copy(color = NoirT2),
                )
            } else {
                Text(
                    text = stringResource(R.string.shop_premium_inactive),
                    style = NoirType.rowTitle.copy(fontSize = 13.5.sp, color = NoirTOff),
                )
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    receipt: EconomyReceipt,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text =
                receiptText(receipt) { resId, args ->
                    context.getString(resId, *args.toTypedArray())
                },
            style = NoirType.rowTitle.copy(fontSize = 13.5.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = DateFormat.getDateTimeInstance().format(Date(receipt.createdAtMs)),
                style = NoirType.kicker.copy(fontSize = 10.5.sp, color = NoirTOff),
            )
            Text(
                text = signedAmount(context, receipt),
                style = NoirType.price.copy(fontSize = 12.5.sp, color = NoirT2),
            )
        }
    }
}

/** Сумма с валютой и знаком: премиум приезжает миллисекундами и уезжает днями. */
private fun signedAmount(
    context: android.content.Context,
    receipt: EconomyReceipt,
): String {
    val body =
        when (receipt.currency) {
            "BOXES" -> context.getString(R.string.receipt_amount_boxes, receipt.amount)
            "GOLD" -> context.getString(R.string.receipt_amount_gold, receipt.amount)
            "NOLICS" -> context.getString(R.string.receipt_amount_nolics, receipt.amount)
            "PREMIUM_MS" -> context.getString(R.string.receipt_amount_premium_days, receipt.amount / 86_400_000)
            else -> context.getString(R.string.receipt_amount_generic, receipt.amount, receipt.currency)
        }
    return if (receipt.amount > 0) "+$body" else body
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
                // The store names its own products: the title from Play wins over our fallback
                // whenever the store answered, so renaming in the console needs no release.
                text = item.storeTitle(),
                style = NoirType.rowTitle.copy(fontSize = 14.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = NoirType.kicker.copy(fontSize = 10.5.sp, color = NoirT2),
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
    // Цена магазина — готовая строка, отформатированная им самим в валюте игрока: показывается
    // как есть и никогда не пересчитывается из priceMicros (NFR4).
    item.storeProduct?.let { product ->
        Text(
            text = product.formattedPrice,
            style = NoirType.num.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        )
        return
    }
    // Пак без ответа магазина цены не имеет — и не показывает вместо неё заглушку: строка под
    // названием объясняет, чего именно ждём.
    if (item.id.storeProductId() != null) return
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
 * Spending currency comes first, then earning it — buying gold with money sits right after the
 * gold-priced heart — then the two that leave the shop entirely.
 */
private val DISPLAY_ORDER =
    listOf(
        ShopItemId.STANDARD_HEART_SLOT,
        ShopItemId.GOLD_HEART,
        ShopItemId.GOLD_PACK_SMALL,
        ShopItemId.GOLD_PACK_MEDIUM,
        ShopItemId.GOLD_PACK_LARGE,
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
            // Золото — стопка монет. Ромб за валюту не отвечает: и сердце за золото, и паки.
            ShopItemId.GOLD_HEART,
            ShopItemId.GOLD_PACK_SMALL,
            ShopItemId.GOLD_PACK_MEDIUM,
            ShopItemId.GOLD_PACK_LARGE,
            -> NoirIcons.GoldStack
            // Подписка — не валюта и не жизнь: звезда читается статусом, а не балансом.
            ShopItemId.PREMIUM -> NoirIcons.Star
            ShopItemId.QUIZ_SLOT -> NoirIcons.Plus
            ShopItemId.AD_REWARD_BOX -> NoirIcons.Box
            ShopItemId.DONATE_GOOGLE_PLAY -> NoirIcons.Heart
            ShopItemId.REFERRAL_PROGRAM -> NoirIcons.Users
            // Рынок имён — не валюта: буквенная иконка, чтобы ромб не читался как золото.
            ShopItemId.NICKNAME_MARKET -> NoirIcons.TypeA
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
        ShopItemId.GOLD_PACK_SMALL -> R.string.shop_item_gold_pack_small
        ShopItemId.GOLD_PACK_MEDIUM -> R.string.shop_item_gold_pack_medium
        ShopItemId.GOLD_PACK_LARGE -> R.string.shop_item_gold_pack_large
        ShopItemId.PREMIUM -> R.string.shop_item_premium
        ShopItemId.QUIZ_SLOT -> R.string.shop_item_quiz_slot
        ShopItemId.AD_REWARD_BOX -> R.string.shop_item_ad_reward_box
        ShopItemId.DONATE_GOOGLE_PLAY -> R.string.shop_item_donate_google_play
        ShopItemId.REFERRAL_PROGRAM -> R.string.shop_item_referral_program
        ShopItemId.NICKNAME_MARKET -> R.string.shop_item_nickname_market
    }

/**
 * The store's own name for its product, falling back to our wording while it has not answered.
 *
 * The fallback names the packs small, medium and large and never an amount: how much gold a SKU
 * grants is the server's secret (NFR3), and a number in the fallback wording would be exactly the
 * copy the economy table forbids.
 */
@Composable
private fun ShopCatalogItem.storeTitle(): String =
    storeProduct?.title?.takeIf { it.isNotBlank() } ?: stringResource(titleRes())

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
            // Потолок и лестница живут в серверной таблице; экран узнаёт и то и другое от
            // элемента витрины, а не из чисел в сборке.
            val next = nextPrice
            if (!isAvailable) {
                base + stringResource(R.string.shop_state_maxed_suffix)
            } else if (next != null) {
                // The card's price is this purchase; the line under the title answers the next one,
                // from the same ladder of costs — so a card never prints one number twice.
                base + stringResource(R.string.shop_state_next_suffix, next.amount.groupedByThousands())
            } else {
                base
            }
        }
        ShopItemId.GOLD_HEART -> stringResource(R.string.shop_state_gold_hearts, state.balance.goldHearts)
        // Пак говорит словами, почему у него нет цены: магазин ещё отвечает, магазин недоступен
        // или товары не выставлены. Замок без объяснения — то, чего полке показывать нельзя.
        ShopItemId.GOLD_PACK_SMALL,
        ShopItemId.GOLD_PACK_MEDIUM,
        ShopItemId.GOLD_PACK_LARGE,
        ShopItemId.PREMIUM,
        ->
            storeProduct?.description?.takeIf { it.isNotBlank() }
                ?: when (state.storeShelf) {
                    StoreShelfStatus.LOADING -> stringResource(R.string.shop_state_store_loading)
                    StoreShelfStatus.NOT_CONFIGURED -> stringResource(R.string.shop_state_store_not_configured)
                    else -> stringResource(R.string.shop_state_store_unreachable)
                }
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
