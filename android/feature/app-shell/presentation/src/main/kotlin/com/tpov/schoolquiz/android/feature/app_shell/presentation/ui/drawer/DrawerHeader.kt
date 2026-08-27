@file:Suppress("MagicNumber", "FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirAvatar
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassCard
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.app_shell.presentation.R
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats

private const val STREAK_TARGET_DAYS = 10

/** Unfilled streak segments: one step above the drawer ground, never a full surface. */
private val NoirSegmentOff = Color(0xFF2A2A32)

/**
 * Who you are, how long your streak is, and what you hold.
 *
 * The five counters used to be emoji with a number underneath. NOIR bans emoji standing in for
 * icons: they render differently on every device, cannot be tinted, and carry a vendor's drawing
 * style into a system that has its own. The streak is ten segments, not a bar, so it reads like
 * the question segments in the runner.
 */
@Composable
fun DrawerHeader(
    userStats: UserStats,
    giftBoxCount: Int,
    modifier: Modifier = Modifier,
) {
    NoirGlassCard(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NoirAvatar(
                    avatarUrl = userStats.avatarUrl,
                    size = 44.dp,
                    borderColor = LocalNoirAccent.current.copy(alpha = 0.28f),
                    fillColor = LocalNoirAccent.current.copy(alpha = 0.12f),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        userStats.nickname.ifBlank { stringResource(R.string.drawer_guest) },
                        style = NoirType.rowTitle,
                    )
                    if (userStats.hasPremium) {
                        Text(stringResource(R.string.drawer_premium), style = NoirType.kicker.copy(color = NoirGold))
                    }
                }
            }
            StreakSegments(streakDays = userStats.streakDays)
            Text(
                stringResource(R.string.drawer_streak_days, userStats.streakDays, STREAK_TARGET_DAYS),
                style = NoirType.kicker.copy(letterSpacing = 0.1.em),
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem(NoirIcons.Heart, userStats.standardHearts.toString(), NoirDanger)
                StatItem(NoirIcons.GoldStack, userStats.goldHearts.toString(), NoirGold)
                StatItem(NoirIcons.Star, userStats.stars.toString(), NoirGold)
                StatItem(NoirIcons.Nolic, userStats.nolics.toString(), LocalNoirAccent.current)
                // The last counter is the gift boxes left to open, not currency — the box icon
                // keeps the number honest.
                StatItem(NoirIcons.Box, giftBoxCount.toString(), NoirT3)
            }
        }
    }
}

/**
 * Ten hairline-separated segments; filled ones carry the accent. A day is a question answered,
 * so the streak borrows the runner's segment language.
 */
@Composable
private fun StreakSegments(streakDays: Int) {
    val accent = LocalNoirAccent.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(STREAK_TARGET_DAYS) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < streakDays) accent else NoirSegmentOff),
            )
        }
    }
}

/** Icon above, number below. The number stays white; only the icon carries the colour. */
@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    tint: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(value, style = NoirType.num.copy(color = NoirT1))
    }
}
