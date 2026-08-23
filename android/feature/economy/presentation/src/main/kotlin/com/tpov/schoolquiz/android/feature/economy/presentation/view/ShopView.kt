@file:Suppress(
    "FunctionNaming",
    "ktlint:standard:function-naming",
    "LongMethod",
    "MagicNumber",
)

package com.tpov.schoolquiz.android.feature.economy.presentation.view

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirBalancePill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIconButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.economy.presentation.R
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopTab
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopViewEvent
import com.tpov.schoolquiz.android.feature.economy.presentation.component.ShopViewState
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCatalogItem
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import java.util.Locale

private const val REFERRAL_TARGET_BOXES = 100
private const val REFERRAL_MIN_SLOTS = 6
private const val REFERRAL_REWARD_BOXES = 50
private val ShopCardShape = RoundedCornerShape(16.dp)
private val ShopButtonShape = RoundedCornerShape(8.dp)
private val ReferralCardShape = RoundedCornerShape(16.dp)
private val ReferralItemShape = RoundedCornerShape(16.dp)
private val LegacyShopCardColor = Color(0xFF242429)
private val LegacyGoldCardColor = Color(0xFF3A2F0A)
private val LegacyDonateCardColor = Color(0xFF2A1B3D)
private val LegacyReferralCardColor = Color(0xFF242429)
private val LegacyBorderColor = Color.White.copy(alpha = 0.2f)
private val LegacyReferralBackground = Color.Black
private val LegacyAccent = Color(0xFF4285F4)
private val LegacyAccentBack = Color(0x1A0288D1)
private val LegacyGold = Color(0xFFFFD700)
private val LegacyAppGold = Color(0xFFD4AF37)
private val LegacyGooglePlay = Color(0xFF4CAF50)
private val LegacySecondaryButton = Color(0xFF555555)
private val LegacyHeart = Color(0xFFFF5252)
private val LegacyRewardBackground = Color(0xFF3A3000)
private val LegacyHighlightText = Color(0xFFFFC107)
private val LegacyWhite80 = Color(0xCCFFFFFF)
private val LegacyTextSecondary = Color(0xFFB8B8B8)
private val LegacyItemBackground = Color(0xFF181818)
private val LegacyAvatarBackground = Color(0xFF333339)

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

    // The ground is drawn once by the shell, which already owns the mode gradient. Painting a
    // second one here mixed two glows and muddied both.
    Column(modifier.fillMaxSize()) {
        ShopHeader(state, onOpenDrawer)
        if (state.message != null) {
            Text(
                text = state.message,
                style = NoirType.rowSub,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        NoirShopStore(
            state = state,
            onPurchase = { id ->
                if (id == ShopItemId.REFERRAL_PROGRAM) {
                    onEvent(ShopViewEvent.SelectTab(ShopTab.REFERRALS))
                } else {
                    onEvent(ShopViewEvent.Purchase(id))
                }
            },
        )
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
            contentDescription = "Open menu",
            onClick = onOpenDrawer,
        )
        Text("Магазин".uppercase(), style = NoirType.appbar, modifier = Modifier.weight(1f))
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
    val referralId = referralProgram.referralIdLabel()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(LegacyReferralBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 16.dp),
        ) {
            item {
                ReferralHeader(
                    onBack = onBack,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
            item {
                ReferralLinkCard(
                    referralId = referralId,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(link))
                        Toast.makeText(context, "Referral link copied!", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        val sendIntent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Join me using my referral link: $link")
                            }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    },
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            }
            item {
                ReferralRewardCard(
                    referralProgram = referralProgram,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            item {
                ReferralSeasonCard(
                    referralProgram = referralProgram,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            }
            item {
                ReferralUsersCard(users = referralProgram.displayUsers())
            }
        }
    }
}

@Composable
private fun ReferralHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegacyCircleIconButton(
            iconRes = R.drawable.ic_back_arrow,
            contentDescription = "Back",
            tint = Color.White,
            onClick = onBack,
        )
        Text(
            text = "Referrals",
            modifier = Modifier.padding(start = 16.dp),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ReferralLinkCard(
    referralId: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LegacyReferralCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Referral link:",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = referralId,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 12.dp),
                color = LegacyWhite80,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LegacyCircleIconButton(
                iconRes = R.drawable.ic_copy,
                contentDescription = "Copy",
                tint = LegacyAccent,
                onClick = onCopy,
            )
            Spacer(modifier = Modifier.width(12.dp))
            LegacyCircleIconButton(
                iconRes = R.drawable.ic_share,
                contentDescription = "Share",
                tint = LegacyAccent,
                onClick = onShare,
            )
        }
    }
}

@Composable
private fun ReferralRewardCard(
    referralProgram: ReferralProgram,
    modifier: Modifier = Modifier,
) {
    val activeUsers = referralProgram.invitedUsers.count { it.allOpenedBoxes >= REFERRAL_TARGET_BOXES }
    val rewardReceived = activeUsers >= REFERRAL_MIN_SLOTS
    LegacyReferralCard(
        modifier = modifier,
        color = LegacyRewardBackground,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (rewardReceived) "Reward received" else "Invite 6 players and receive",
                modifier = Modifier.weight(1f),
                color = LegacyHighlightText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
            )
            ReferralBoxCountBadge(
                count = REFERRAL_REWARD_BOXES.toString(),
                gold = true,
                iconRes = if (rewardReceived) R.drawable.ic_save else R.drawable.ic_box,
            )
        }
    }
}

@Composable
private fun ReferralSeasonCard(
    referralProgram: ReferralProgram,
    modifier: Modifier = Modifier,
) {
    LegacyReferralCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "At the end of season you will receive boxes:",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "You receive 1% of all opened boxes from all your invited friends.",
                    color = LegacyTextSecondary,
                    fontSize = 12.sp,
                )
            }
            ReferralBoxCountBadge(
                count = referralProgram.seasonBonusBoxes().toString(),
                gold = false,
                iconRes = R.drawable.ic_box,
            )
        }
    }
}

@Composable
private fun ReferralUsersCard(
    users: List<ReferralUserDisplay>,
    modifier: Modifier = Modifier,
) {
    LegacyReferralCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Your Referrals",
                modifier = Modifier.padding(bottom = 12.dp),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                users.forEach { user ->
                    ReferralUserItem(user = user)
                }
            }
        }
    }
}

@Composable
private fun ReferralUserItem(user: ReferralUserDisplay) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!user.isPlaceholder) {
            ReferralProgress(user = user)
        }
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
            shape = ReferralItemShape,
            color = LegacyItemBackground,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = LegacyAvatarBackground,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_person),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .alpha(if (user.isPlaceholder) 0.5f else 1f),
                            tint = Color.White,
                        )
                    }
                }
                Text(
                    text = user.nickname,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    color = if (user.isPlaceholder) Color.LightGray else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    ReferralStatRow(
                        label = "Open boxes:",
                        value = if (user.isPlaceholder) "-" else user.allOpenedBoxes.toString(),
                        valueColor = if (user.isPlaceholder) Color.LightGray else Color.White,
                        labelColor = if (user.isPlaceholder) Color.LightGray else Color.White,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ReferralStatRow(
                        label = "Your Bonus:",
                        value = if (user.isPlaceholder) "-" else user.bonusLabel,
                        valueColor = if (user.isPlaceholder) Color.LightGray else LegacyAccent,
                        labelColor = if (user.isPlaceholder) Color.LightGray else Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferralProgress(user: ReferralUserDisplay) {
    val progress = user.progressPercent
    val activated = progress >= REFERRAL_TARGET_BOXES
    val progressFraction = progress / REFERRAL_TARGET_BOXES.toFloat()

    Column(
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (activated) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "ACTIVATED",
                    color = LegacyAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = "$progress%",
                    color = LegacyWhite80,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${REFERRAL_TARGET_BOXES - progress} boxes to activate",
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    color = LegacyTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LegacyShopCardColor),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(progressFraction)
                        .height(1.dp)
                        .background(LegacyAccent),
            )
        }
    }
}

@Composable
private fun ReferralStatRow(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(end = 8.dp),
            color = labelColor,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
        )
        Text(
            text = value,
            modifier = Modifier.widthIn(min = 40.dp),
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ReferralBoxCountBadge(
    count: String,
    gold: Boolean,
    iconRes: Int,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (gold) LegacyGoldCardColor else LegacyAccentBack,
        border = BorderStroke(1.dp, if (gold) LegacyAppGold else LegacyAccent),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = count,
                color = if (gold) LegacyAppGold else LegacyAccent,
                fontSize = 20.sp,
            )
            if (iconRes == R.drawable.ic_box) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (gold) LegacyAppGold else LegacyAccent,
                )
            }
        }
    }
}

@Composable
private fun LegacyReferralCard(
    modifier: Modifier = Modifier,
    color: Color = LegacyReferralCardColor,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ReferralCardShape,
        color = color,
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        content()
    }
}

@Composable
private fun LegacyCircleIconButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .size(40.dp)
                .clickable(onClick = onClick),
        shape = CircleShape,
        color = LegacyAccentBack,
        contentColor = tint,
        border = BorderStroke(1.dp, LegacyAccent),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
        }
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

private fun ReferralProgram.referralIdLabel(): String {
    val id =
        link
            .substringAfter("id=", missingDelimiterValue = link)
            .substringBefore('&')
            .substringAfterLast('/')
            .ifBlank { "guest" }
    return "tpovId: $id"
}

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
        val slot = users.size + 1
        users +=
            ReferralUserDisplay(
                id = "empty-slot-$slot",
                nickname = "Empty Slot",
                allOpenedBoxes = 0,
                seasonBoxes = 0,
                isPlaceholder = true,
            )
    }
    return users
}

private fun ReferralProgram.seasonBonusBoxes(): Int = invitedUsers.sumOf { it.seasonBoxes / 100 }

private fun ShopCatalogItem.icon(): ImageVector =
    when (id) {
        ShopItemId.STANDARD_HEART_SLOT -> Icons.Default.Favorite
        ShopItemId.GOLD_HEART -> Icons.Default.Diamond
        ShopItemId.QUIZ_SLOT -> Icons.Default.AddCircle
        ShopItemId.DONATE_GOOGLE_PLAY -> Icons.Default.VolunteerActivism
        ShopItemId.REFERRAL_PROGRAM -> Icons.Default.Groups
        ShopItemId.AD_REWARD_BOX -> Icons.Default.OndemandVideo
    }
