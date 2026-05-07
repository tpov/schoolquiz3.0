package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignCard
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignDeepSurfaceColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignLightBorderColor

private const val MIN_PERCENT_VALUE = 0
private const val MAX_PERCENT_VALUE = 100

internal data class TournamentLeaderboardUi(
    val title: String,
    val stageLabel: String,
    val qualificationRule: String,
    val currentUserNickname: String,
    val currentUserPercent: Int?,
    val standings: List<TournamentStandingUi>,
    val isLoading: Boolean,
    val errorMessage: String?,
)

internal data class TournamentStandingUi(
    val nickname: String,
    val percent: Int,
    val attempts: Int,
)

internal data class TournamentParticipantsUi(
    val title: String,
    val stageLabel: String,
    val participants: List<TournamentParticipantUi>,
    val isLoading: Boolean,
    val errorMessage: String?,
)

internal data class TournamentParticipantUi(
    val nickname: String,
    val status: String,
    val attempts: Int,
)

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun TournamentLeaderboardScreen(
    model: TournamentLeaderboardUi,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TournamentHeaderCard(
                icon = Icons.Default.EmojiEvents,
                title = "Лидерборд",
                subtitle = model.title,
                detail = model.stageLabel,
            )
        }
        item {
            TournamentMetricCard(
                title = "Мой результат",
                value = model.currentUserPercent?.let { "$it%" } ?: "нет попытки",
                subtitle = model.currentUserNickname,
            )
        }
        item {
            TournamentMetricCard(
                title = "Правило отбора",
                value = model.qualificationRule,
                subtitle = "обновляется после серверной синхронизации",
            )
        }
        if (model.isLoading) {
            item {
                TournamentLoadingCard()
            }
        }
        model.errorMessage?.let { message ->
            item {
                TournamentEmptyCard(
                    title = "Не удалось обновить таблицу",
                    subtitle = message,
                )
            }
        }
        if (model.standings.isEmpty()) {
            item {
                TournamentEmptyCard(
                    title = "Пока нет результатов",
                    subtitle = "После первых прохождений здесь появится общая таблица.",
                )
            }
        } else {
            itemsIndexed(model.standings) { index, standing ->
                TournamentStandingRow(rank = index + 1, standing = standing)
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun TournamentParticipantsScreen(
    model: TournamentParticipantsUi,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TournamentHeaderCard(
                icon = Icons.Default.Groups,
                title = "Список участников",
                subtitle = model.title,
                detail = model.stageLabel,
            )
        }
        item {
            TournamentMetricCard(
                title = "Участников",
                value = model.participants.size.toString(),
                subtitle = "серверный список",
            )
        }
        if (model.isLoading) {
            item {
                TournamentLoadingCard()
            }
        }
        model.errorMessage?.let { message ->
            item {
                TournamentEmptyCard(
                    title = "Не удалось обновить участников",
                    subtitle = message,
                )
            }
        }
        itemsIndexed(model.participants) { _, participant ->
            TournamentParticipantRow(participant = participant)
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentHeaderCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    detail: String,
) {
    SchoolQuizDesignCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = schoolQuizDesignDeepSurfaceColor(),
        borderColor = schoolQuizDesignLightBorderColor(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentLoadingCard() {
    SchoolQuizDesignCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = schoolQuizDesignDeepSurfaceColor(),
        borderColor = schoolQuizDesignLightBorderColor(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Обновляем данные",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentMetricCard(
    title: String,
    value: String,
    subtitle: String,
) {
    SchoolQuizDesignCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = schoolQuizDesignDeepSurfaceColor(),
        borderColor = schoolQuizDesignLightBorderColor(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentEmptyCard(
    title: String,
    subtitle: String,
) {
    SchoolQuizDesignCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = schoolQuizDesignDeepSurfaceColor(),
        borderColor = schoolQuizDesignLightBorderColor(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Timeline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentStandingRow(
    rank: Int,
    standing: TournamentStandingUi,
) {
    SchoolQuizDesignCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = schoolQuizDesignDeepSurfaceColor(),
        borderColor = schoolQuizDesignLightBorderColor(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$rank. ${standing.nickname}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${standing.percent}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = {
                    standing.percent.coerceIn(MIN_PERCENT_VALUE, MAX_PERCENT_VALUE) / MAX_PERCENT_VALUE.toFloat()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Попыток: ${standing.attempts}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentParticipantRow(participant: TournamentParticipantUi) {
    SchoolQuizDesignCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = schoolQuizDesignDeepSurfaceColor(),
        borderColor = schoolQuizDesignLightBorderColor(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = participant.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = participant.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                )
                Text(
                    text = "Попыток: ${participant.attempts}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                )
            }
        }
    }
}
