@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.quest.presentation.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.CatalogGrid
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeGiftBoxOpeningState
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxReward

/**
 * Home (catalog grid) screen.
 *
 * Shows CatalogGrid (2-column grid with titleMedium bold typography, 16dp corners, 12dp gap).
 * Archived catalogs excluded at DAO level (WHERE archived=0).
 *
 * Spec: AC#21-22; DFD 2 (02-behavior.md)
 */
@Composable
fun HomeQuestsScreen(
    component: HomeQuestsComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()

    HomeQuestsContent(
        state = state,
        onCatalogClick = component::onCatalogClick,
        onGiftBoxFabClick = component::onGiftBoxFabClick,
        onGiftBoxDismiss = component::onGiftBoxDismiss,
        modifier = modifier,
    )
}

@Composable
private fun HomeQuestsContent(
    state: HomeQuestsUiState,
    onCatalogClick: (CatalogId, String) -> Unit,
    onGiftBoxFabClick: () -> Unit,
    onGiftBoxDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingContent(modifier = Modifier.fillMaxSize())
            state.catalogs.isEmpty() -> EmptyContent(modifier = Modifier.fillMaxSize())
            else ->
                CatalogGrid(
                    items = state.catalogs,
                    onCatalogClick = onCatalogClick,
                    modifier = Modifier.fillMaxSize(),
                )
        }

        if (state.giftBoxCount > 0 && state.giftBoxOpening !is HomeGiftBoxOpeningState.Opening) {
            GiftBoxFab(
                count = state.giftBoxCount,
                onClick = onGiftBoxFabClick,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
            )
        }

        state.giftBoxOpening?.let { opening ->
            GiftBoxOpeningOverlay(
                opening = opening,
                onDismiss = onGiftBoxDismiss,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Нет каталогов",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Синхронизируйте данные через меню",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun GiftBoxFab(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        BadgedBox(
            badge = {
                Badge {
                    Text(count.toString())
                }
            },
        ) {
            Icon(
                imageVector = Icons.Default.CardGiftcard,
                contentDescription = "Открыть коробку",
            )
        }
    }
}

@Composable
private fun GiftBoxOpeningOverlay(
    opening: HomeGiftBoxOpeningState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = SCRIM_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GiftBoxOverlayHeader(
                    opening = opening,
                    onDismiss = onDismiss,
                )
                GiftBoxAnimation(opening = opening)
                GiftBoxOverlayBody(opening = opening)
                GiftBoxOverlayActions(
                    opening = opening,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun GiftBoxOverlayHeader(
    opening: HomeGiftBoxOpeningState,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Коробка",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = opening.subtitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (opening !is HomeGiftBoxOpeningState.Opening) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть")
            }
        }
    }
}

@Composable
private fun GiftBoxAnimation(opening: HomeGiftBoxOpeningState) {
    val isOpening = opening is HomeGiftBoxOpeningState.Opening
    val shakeTransition = rememberInfiniteTransition(label = "gift_box_shake")
    val shake by shakeTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 120),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "gift_box_rotation",
    )
    val boxScale by animateFloatAsState(
        targetValue =
            when (opening) {
                is HomeGiftBoxOpeningState.Opening -> 1.08f
                is HomeGiftBoxOpeningState.Opened -> 1.18f
                is HomeGiftBoxOpeningState.Failed -> 1f
            },
        animationSpec = tween(durationMillis = 260),
        label = "gift_box_scale",
    )

    Surface(
        modifier = Modifier.size(152.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.CardGiftcard,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(112.dp)
                        .rotate(if (isOpening) shake else 0f)
                        .scale(boxScale),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun GiftBoxOverlayBody(opening: HomeGiftBoxOpeningState) {
    when (opening) {
        is HomeGiftBoxOpeningState.Opening ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Text(
                    text = "Открываем...",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        is HomeGiftBoxOpeningState.Opened ->
            RewardContent(opening = opening)
        is HomeGiftBoxOpeningState.Failed ->
            Text(
                text = opening.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
    }
}

@Composable
private fun RewardContent(opening: HomeGiftBoxOpeningState.Opened) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = opening.reward.icon(),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = opening.reward.rewardText(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "Осталось коробок: ${opening.remainingBoxCount}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!opening.profileSynced) {
            Text(
                text = "Профиль обновится после следующей синхронизации",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GiftBoxOverlayActions(
    opening: HomeGiftBoxOpeningState,
    onDismiss: () -> Unit,
) {
    when (opening) {
        is HomeGiftBoxOpeningState.Opening ->
            Text(
                text = "Запрос к серверу уже отправлен",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        is HomeGiftBoxOpeningState.Opened ->
            Button(onClick = onDismiss) {
                Text("Забрать")
            }
        is HomeGiftBoxOpeningState.Failed ->
            OutlinedButton(onClick = onDismiss) {
                Text("Закрыть")
            }
    }
}

private fun HomeGiftBoxOpeningState.subtitle(): String =
    when (this) {
        is HomeGiftBoxOpeningState.Opening -> "Трясём коробку и ждём награду"
        is HomeGiftBoxOpeningState.Opened -> "Награда начислена в профиль"
        is HomeGiftBoxOpeningState.Failed -> "Открытие не удалось"
    }

private fun GiftBoxReward.icon(): ImageVector =
    when (this) {
        is GiftBoxReward.Nolics -> Icons.Default.Tag
        is GiftBoxReward.Gold -> Icons.Default.Diamond
        is GiftBoxReward.Premium -> Icons.Default.WorkspacePremium
        is GiftBoxReward.Logo -> Icons.Default.Star
        is GiftBoxReward.Trophy -> Icons.Default.EmojiEvents
    }

private fun GiftBoxReward.rewardText(): String =
    when (this) {
        is GiftBoxReward.Nolics -> "$amount ноликов"
        is GiftBoxReward.Gold -> "$amount золота"
        is GiftBoxReward.Premium -> "${premiumDays()} дн. премиума"
        is GiftBoxReward.Logo -> "Логотип: $itemName"
        is GiftBoxReward.Trophy -> "$amount трофеев"
    }

private fun GiftBoxReward.Premium.premiumDays(): Long = (amount / SECONDS_IN_DAY).coerceAtLeast(1L)

private const val SECONDS_IN_DAY = 86_400L
private const val SCRIM_ALPHA = 0.54f
