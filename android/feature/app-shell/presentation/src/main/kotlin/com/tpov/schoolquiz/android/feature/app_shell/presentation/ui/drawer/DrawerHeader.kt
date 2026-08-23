@file:Suppress("MagicNumber", "FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassCard
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirProgressBar
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats

private const val STREAK_TARGET_DAYS = 10

/**
 * Who you are, how long your streak is, and what you hold.
 *
 * The five counters used to be emoji with a number underneath. NOIR bans emoji standing in for
 * icons: they render differently on every device, cannot be tinted, and carry a vendor's drawing
 * style into a system that has its own.
 */
@Composable
fun DrawerHeader(
    userStats: UserStats,
    modifier: Modifier = Modifier,
) {
    NoirGlassCard(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(LocalNoirAccent.current.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, LocalNoirAccent.current.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = NoirIcons.Users,
                        contentDescription = null,
                        tint = LocalNoirAccent.current,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(userStats.nickname.ifBlank { "Гость" }, style = NoirType.rowTitle)
                    if (userStats.hasPremium) {
                        Text("Premium", style = NoirType.kicker.copy(color = NoirGold))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                NoirProgressBar(
                    fraction = (userStats.streakDays.toFloat() / STREAK_TARGET_DAYS).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Серия ${userStats.streakDays} / $STREAK_TARGET_DAYS дней",
                    style = NoirType.kicker,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem(NoirIcons.Heart, "${userStats.standardHearts}", NoirDanger)
                StatItem(NoirIcons.Gem, "${userStats.goldHearts}", NoirGold)
                StatItem(NoirIcons.Star, "${userStats.stars}", NoirGold)
                StatItem(NoirIcons.Nolic, "${userStats.nolics}", LocalNoirAccent.current)
                StatItem(NoirIcons.GoldStack, "${userStats.gold}", NoirGold)
            }
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
