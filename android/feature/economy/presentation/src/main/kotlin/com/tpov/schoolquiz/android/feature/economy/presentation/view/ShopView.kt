@file:Suppress(
    "FunctionNaming",
    "ktlint:standard:function-naming",
    "LongMethod",
    "MagicNumber",
)

package com.tpov.schoolquiz.android.feature.economy.presentation.view

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirAvatar
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirBalancePill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassCard
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIconButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirItemTile
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirProgressBar
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirRow
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSectionRule
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirWashShop
import com.tpov.schoolquiz.android.core.designsystem.noir.noirScreenWash
import com.tpov.schoolquiz.android.feature.economy.presentation.R
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopMessage
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopTab
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopViewEvent
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopViewState
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import java.util.Locale

private const val REFERRAL_TARGET_BOXES = 100
private const val REFERRAL_MIN_SLOTS = 6
private const val REFERRAL_REWARD_BOXES = 50

@Composable
fun ShopView(
    state: ShopViewState,
    onEvent: (ShopViewEvent) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.selectedTab == ShopTab.REFERRALS) {
        ReferralProgramView(
            referralProgram = state.referralProgram,
            onBack = { onEvent(ShopViewEvent.SelectTab(ShopTab.STORE)) },
            modifier = modifier,
        )
        return
    }

    if (state.selectedTab == ShopTab.NICKNAMES) {
        // Its own screen, reached from the shop row — the same shape as Referrals. A tab strip
        // above the store would say the shop has two halves, and it has one with doors in it.
        Column(modifier.fillMaxSize().noirScreenWash(NoirWashShop)) {
            NicknameMarketHeader(
                balance = state.balance,
                onBack = { onEvent(ShopViewEvent.SelectTab(ShopTab.STORE)) },
            )
            state.message?.let { message ->
                Text(
                    text = message.resolvedText(),
                    style = NoirType.rowSub,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            NoirNicknameMarket(state = state, onEvent = onEvent)
        }
        return
    }

    // The ground is drawn once by the shell, which already owns the mode gradient. Painting a
    // second one here mixed two glows and muddied both.
    Column(modifier.fillMaxSize().noirScreenWash(NoirWashShop)) {
        ShopHeader(state, onOpenDrawer)
        state.message?.let { message ->
            Text(
                text = message.resolvedText(),
                style = NoirType.rowSub,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        NoirShopStore(
            state = state,
            onPurchase = { id ->
                when (id) {
                    ShopItemId.REFERRAL_PROGRAM -> onEvent(ShopViewEvent.SelectTab(ShopTab.REFERRALS))
                    ShopItemId.NICKNAME_MARKET -> onEvent(ShopViewEvent.SelectTab(ShopTab.NICKNAMES))
                    else -> onEvent(ShopViewEvent.Purchase(id))
                }
            },
        )
    }
}

/**
 * Back, a title and both balances — the same bar the store carries.
 *
 * The balances belong here more than anywhere: this is the one screen where every price is in gold
 * and the answer to "can I afford it" is the reason somebody looks up.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun NicknameMarketHeader(
    balance: EconomyResourceBalance,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 13.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NoirIconButton(
            icon = NoirIcons.Back,
            contentDescription = stringResource(R.string.shop_cd_back),
            onClick = onBack,
        )
        Text(stringResource(R.string.shop_title_nft), style = NoirType.appbar, modifier = Modifier.weight(1f))
        // The canvas carries gold alone in this bar, and tints the number itself: on the one screen
        // where every price is gold, the second currency only muddies which one buys things.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(NoirIcons.GoldStack, contentDescription = null, tint = NoirGold, modifier = Modifier.size(13.dp))
            Text(
                text = balance.gold.toString(),
                style = NoirType.num.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = NoirGold,
            )
        }
    }
}

/** Title and both balances. The icon carries the currency; the numbers stay white. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ShopHeader(
    state: ShopViewState,
    onOpenDrawer: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 13.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NoirIconButton(
            icon = NoirIcons.Menu,
            contentDescription = stringResource(R.string.shop_cd_open_menu),
            onClick = onOpenDrawer,
        )
        Text(
            stringResource(R.string.shop_title_store).uppercase(),
            style = NoirType.appbar,
            modifier = Modifier.weight(1f),
        )
        NoirBalancePill(
            icon = NoirIcons.Nolic,
            value = state.balance.nolics.toString(),
            tint = LocalNoirAccent.current,
        )
        NoirBalancePill(
            icon = NoirIcons.GoldStack,
            value = state.balance.gold.toString(),
            tint = NoirGold,
        )
    }
}

/** Title and both balances. The icon carries the currency; the numbers stay white. */
@Composable
private fun ReferralProgramView(
    referralProgram: ReferralProgram,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val link = referralProgram.link
    val referralId =
        stringResource(
            R.string.shop_referral_id_format,
            referralProgram.referralId(context.getString(R.string.shop_referral_guest)),
        )

    Column(
        modifier =
            modifier
                .fillMaxSize()
                // Рефералы наследуют магазин: тот же wash-shop, тот же бар, те же строки.
                .noirScreenWash(NoirWashShop),
    ) {
        ReferralHeader(onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ReferralLinkCard(
                    referralId = referralId,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(link))
                        Toast.makeText(context, R.string.shop_toast_link_copied, Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        val sendIntent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.shop_share_message, link))
                            }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    },
                )
            }
            item { ReferralRewardCard(referralProgram = referralProgram) }
            item { ReferralSeasonCard(referralProgram = referralProgram) }
            item { ReferralUsersCard(users = referralProgram.displayUsers()) }
        }
    }
}

/** Back and a title — the same bar the nickname market carries. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ReferralHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 13.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NoirIconButton(
            icon = NoirIcons.Back,
            contentDescription = stringResource(R.string.shop_cd_back),
            onClick = onBack,
        )
        Text(stringResource(R.string.shop_referral_title).uppercase(), style = NoirType.appbar)
    }
}

/** The id leads, the two actions sit in the shop's right-hand action column. */
@Composable
private fun ReferralLinkCard(
    referralId: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NoirGlassCard(modifier = modifier) {
        NoirItemTile(icon = NoirIcons.Users)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(R.string.shop_referral_link_label).uppercase(),
                style = NoirType.kicker,
            )
            Text(
                text = referralId,
                style = NoirType.num.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ReferralTextAction(stringResource(R.string.shop_cd_copy), onCopy)
            ReferralTextAction(stringResource(R.string.shop_cd_share), onShare)
        }
    }
}

@Composable
private fun ReferralTextAction(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text.uppercase(),
        style = NoirType.button.copy(fontSize = 11.sp, color = LocalNoirAccent.current),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ReferralRewardCard(
    referralProgram: ReferralProgram,
    modifier: Modifier = Modifier,
) {
    val activeUsers = referralProgram.invitedUsers.count { it.allOpenedBoxes >= REFERRAL_TARGET_BOXES }
    val rewardReceived = activeUsers >= REFERRAL_MIN_SLOTS
    NoirGlassCard(modifier = modifier) {
        NoirItemTile(icon = NoirIcons.Box, tint = NoirGold)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text =
                    if (rewardReceived) {
                        stringResource(R.string.shop_referral_reward_received)
                    } else {
                        stringResource(R.string.shop_referral_reward_invite)
                    },
                style = NoirType.rowTitle.copy(color = NoirGold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ReferralBoxCount(count = REFERRAL_REWARD_BOXES.toString(), color = NoirGold)
    }
}

@Composable
private fun ReferralSeasonCard(
    referralProgram: ReferralProgram,
    modifier: Modifier = Modifier,
) {
    NoirGlassCard(modifier = modifier) {
        NoirItemTile(icon = NoirIcons.Calendar)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(R.string.shop_referral_season_header),
                style = NoirType.rowTitle,
                color = NoirT1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.shop_referral_season_note),
                style = NoirType.rowSub,
                color = NoirT2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ReferralBoxCount(count = referralProgram.seasonBonusBoxes().toString(), color = NoirT3)
    }
}

/** A box payout: the number keeps its tabular mono, zeros read grey. */
@Composable
private fun ReferralBoxCount(
    count: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = count,
            style = NoirType.num.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            color = color,
        )
        Icon(NoirIcons.Box, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ReferralUsersCard(
    users: List<ReferralUserDisplay>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NoirSectionRule(
            label = stringResource(R.string.shop_referral_users_title),
            trailing = users.count { !it.isPlaceholder }.toString(),
        )
        users.forEach { user ->
            ReferralUserItem(user = user)
        }
    }
}

@Composable
private fun ReferralUserItem(user: ReferralUserDisplay) {
    val filled = !user.isPlaceholder
    Column(modifier = Modifier.fillMaxWidth()) {
        NoirRow(
            leading = { NoirAvatar(avatarUrl = null, size = 40.dp) },
            trailing = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ReferralStatRow(
                        label = stringResource(R.string.shop_referral_stat_open_boxes),
                        value = if (filled) user.allOpenedBoxes.toString() else "–",
                        valueColor = if (filled) NoirT1 else NoirTOff,
                    )
                    ReferralStatRow(
                        label = stringResource(R.string.shop_referral_stat_bonus),
                        value = if (filled) user.bonusLabel else "–",
                        valueColor = if (filled) LocalNoirAccent.current else NoirTOff,
                    )
                }
            },
        ) {
            Text(
                text = if (filled) user.nickname else stringResource(R.string.shop_referral_empty_slot),
                style = NoirType.rowTitle.copy(color = if (filled) NoirT1 else NoirTOff),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (filled) {
                Spacer(Modifier.height(4.dp))
                NoirProgressBar(fraction = user.progressPercent / REFERRAL_TARGET_BOXES.toFloat())
                if (user.progressPercent >= REFERRAL_TARGET_BOXES) {
                    Text(
                        text = stringResource(R.string.shop_referral_activated),
                        style = NoirType.kicker.copy(fontSize = 10.sp, color = LocalNoirAccent.current),
                    )
                } else {
                    Text(
                        text =
                            stringResource(
                                R.string.shop_referral_boxes_left,
                                REFERRAL_TARGET_BOXES - user.progressPercent,
                            ),
                        style = NoirType.rowSub.copy(fontSize = 11.sp),
                        color = NoirT3,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferralStatRow(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, style = NoirType.rowSub.copy(fontSize = 11.sp), color = NoirT3)
        Text(
            text = value,
            style = NoirType.num.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = valueColor,
        )
    }
}

private data class ReferralUserDisplay(
    val id: String,
    val nickname: String,
    val allOpenedBoxes: Int,
    val seasonBoxes: Int,
    val isPlaceholder: Boolean,
) {
    val progressPercent: Int = allOpenedBoxes.coerceIn(0, REFERRAL_TARGET_BOXES)
    val bonusLabel: String = String.format(Locale.US, "%.1f", seasonBoxes / 100.0)
}

private fun ReferralProgram.referralId(guestFallback: String): String =
    link
        .substringAfter("id=", missingDelimiterValue = link)
        .substringBefore('&')
        .substringAfterLast('/')
        .ifBlank { guestFallback }

private fun ReferralProgram.displayUsers(): List<ReferralUserDisplay> {
    val users =
        invitedUsers
            .sortedByDescending { it.allOpenedBoxes }
            .map {
                ReferralUserDisplay(
                    id = it.id,
                    nickname = it.nickname,
                    allOpenedBoxes = it.allOpenedBoxes,
                    seasonBoxes = it.seasonBoxes,
                    isPlaceholder = false,
                )
            }
            .toMutableList()
    while (users.size < REFERRAL_MIN_SLOTS) {
        users +=
            ReferralUserDisplay(
                id = "empty-slot-${users.size + 1}",
                nickname = "",
                allOpenedBoxes = 0,
                seasonBoxes = 0,
                isPlaceholder = true,
            )
    }
    return users
}

/** Picks the words for what the component reported, including which language they are. */
@Composable
private fun ShopMessage.resolvedText(): String =
    when (this) {
        is ShopMessage.NicknameClaimed ->
            if (charged > 0) {
                stringResource(R.string.nft_msg_claimed_paid, charged)
            } else {
                stringResource(R.string.nft_msg_claimed_free)
            }
        is ShopMessage.NicknameWorn -> stringResource(R.string.nft_msg_worn, nickname)
        is ShopMessage.NicknameListed -> stringResource(R.string.nft_msg_listed, price)
        ShopMessage.ListingCancelQueued -> stringResource(R.string.nft_msg_listing_cancel_queued)
        is ShopMessage.NicknameBought -> stringResource(R.string.nft_msg_bought, commission)
        is ShopMessage.LogoPurchased -> stringResource(R.string.nft_msg_logo_bought, charged)
        is ShopMessage.LogoWorn -> stringResource(R.string.nft_msg_logo_worn, logo)
        is ShopMessage.LogoListed -> stringResource(R.string.nft_msg_logo_listed, price)
        is ShopMessage.LogoBoughtListed -> stringResource(R.string.nft_msg_logo_bought_listed, commission)
        ShopMessage.ShopUnavailable -> stringResource(R.string.shop_msg_unavailable)
        is ShopMessage.Notice -> text
        is ShopMessage.Failure -> detail ?: stringResource(R.string.shop_msg_action_failed)
    }

private fun ReferralProgram.seasonBonusBoxes(): Int = invitedUsers.sumOf { it.seasonBoxes / 100 }
