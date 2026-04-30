package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.BrandProgressBar
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignCard
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignLightBorderColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignNeutralBorderColor
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun DrawerHeader(
    userStats: UserStats,
    modifier: Modifier = Modifier,
) {
    SchoolQuizDesignCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.14f),
        borderColor = schoolQuizDesignLightBorderColor(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(1.dp, schoolQuizDesignLightBorderColor()),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Avatar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Column {
                    Text(
                        text = userStats.nickname.ifBlank { "Гость" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (userStats.hasPremium) {
                        Text(
                            text = "Premium",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            BrandProgressBar(
                progress = (userStats.streakDays / 10f).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(5.dp))

            Text(
                text = "Серия: ${userStats.streakDays}/10 дней",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem(label = "♥", value = "${userStats.standardHearts}")
                StatItem(label = "💛", value = "${userStats.goldHearts}")
                StatItem(label = "⭐", value = "${userStats.stars}")
                StatItem(label = "◎", value = "${userStats.nolics}")
                StatItem(label = "🪙", value = "${userStats.gold}")
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun StatItem(
    label: String,
    value: String,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, schoolQuizDesignNeutralBorderColor()),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
