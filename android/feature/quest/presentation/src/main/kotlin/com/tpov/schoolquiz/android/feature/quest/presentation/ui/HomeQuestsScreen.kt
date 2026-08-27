@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.quest.presentation.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.components.CatalogGrid
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirBgDeep
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassCard
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirInk
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.core.designsystem.noir.noirScreenWash
import com.tpov.schoolquiz.android.feature.quest.presentation.ContinueLessonUi
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeGiftBoxFailure
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeGiftBoxOpeningState
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsUiState
import com.tpov.schoolquiz.android.feature.quest.presentation.LessonSegmentUi
import com.tpov.schoolquiz.android.feature.quest.presentation.R
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
    canManagePublicShelves: Boolean = false,
    onAddPublicQuestClick: () -> Unit = {},
) {
    val state by component.state.collectAsState()

    HomeQuestsContent(
        state = state,
        onCatalogClick = component::onCatalogClick,
        onContinueClick = component::onContinueClick,
        onGiftBoxFabClick = component::onGiftBoxFabClick,
        onGiftBoxDismiss = component::onGiftBoxDismiss,
        canManagePublicShelves = canManagePublicShelves,
        onAddPublicQuestClick = onAddPublicQuestClick,
        modifier = modifier,
    )
}

/** Midpoint of the home wash, from the drawing — a shade lighter than the profile's. */
private val NoirHomeWash = androidx.compose.ui.graphics.Color(0xFF122634)

@Composable
private fun HomeQuestsContent(
    state: HomeQuestsUiState,
    onCatalogClick: (CatalogId, String) -> Unit,
    onContinueClick: () -> Unit,
    onGiftBoxFabClick: () -> Unit,
    onGiftBoxDismiss: () -> Unit,
    canManagePublicShelves: Boolean,
    onAddPublicQuestClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().noirScreenWash(NoirHomeWash)) {
        when {
            state.isLoading -> LoadingContent(modifier = Modifier.fillMaxSize())
            state.catalogs.isEmpty() -> EmptyContent(modifier = Modifier.fillMaxSize())
            else ->
                CatalogGrid(
                    items = state.catalogs,
                    onCatalogClick = onCatalogClick,
                    modifier = Modifier.fillMaxSize(),
                    header =
                        state.continueLesson?.let { lesson ->
                            {
                                ContinueLessonCard(
                                    lesson = lesson,
                                    onClick = onContinueClick,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 4.dp),
                                )
                            }
                        },
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
        if (canManagePublicShelves) {
            AddPublicQuestFab(
                onClick = onAddPublicQuestClick,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
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

/**
 * The lesson the player was last mid-way through, on a glass plate above the catalogs.
 *
 * The card is one tap target, exactly like the canvas: kicker, counter, chevron, title, path and
 * segment progress are all inside the clickable surface.
 */
@Composable
private fun ContinueLessonCard(
    lesson: ContinueLessonUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalNoirAccent.current
    NoirGlassCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.quest_continue).uppercase(),
                    style = NoirType.kicker.copy(color = accent),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text =
                        stringResource(
                            R.string.quest_continue_progress,
                            lesson.completedCount,
                            lesson.lessonSegments.size,
                        ),
                    style = NoirType.num.copy(fontSize = 10.5.sp, color = NoirTOff),
                )
                Icon(
                    imageVector = NoirIcons.ChevronRight,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = lesson.title,
                style = NoirType.question.copy(fontSize = 18.sp, lineHeight = 22.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (lesson.path.isNotBlank()) {
                Text(
                    text = lesson.path,
                    style = NoirType.rowSub.copy(color = NoirTOff),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SectionLessonSegments(segments = lesson.lessonSegments)
        }
    }
}

/** How many of the section's lessons are already done — the counter beside the kicker. */
private val ContinueLessonUi.completedCount: Int
    get() = lessonSegments.count { it.completed }

/**
 * Segment strip for the continue card: one slot per lesson of the section, in teaching order.
 *
 * Filled means the lesson is done, the faint one marks the lesson being continued, and the dark
 * slots are still ahead — the same language the runner uses for questions.
 */
@Composable
private fun SectionLessonSegments(segments: List<LessonSegmentUi>) {
    val accent = LocalNoirAccent.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEach { segment ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            segment.completed -> accent
                            segment.isCurrent -> accent.copy(alpha = 0.55f)
                            else -> NoirContinueSegmentOff
                        },
                    ),
            )
        }
    }
}

private val NoirContinueSegmentOff = Color(0xFF2E2E36)

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.quest_empty_catalogs_title),
                style = NoirType.appbar,
            )
            Text(
                text = stringResource(R.string.quest_empty_catalogs_hint),
                style = NoirType.rowSub,
            )
            Text(
                text = stringResource(R.string.quest_sync),
                style = NoirType.button.copy(color = LocalNoirAccent.current),
            )
        }
    }
}

@Composable
private fun AddPublicQuestFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.quest_cd_add_quest),
        )
    }
}

@Composable
private fun GiftBoxFab(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalNoirAccent.current
    val fabShape = RoundedCornerShape(18.dp)
    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .size(58.dp)
                    // The canvas lights the button with an accent glow plus a drop shadow.
                    .shadow(
                        elevation = 14.dp,
                        shape = fabShape,
                        ambientColor = accent.copy(alpha = 0.42f),
                        spotColor = accent.copy(alpha = 0.42f),
                    )
                    .clip(fabShape)
                    .background(accent)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.CardGiftcard,
                contentDescription = stringResource(R.string.quest_cd_open_box),
                tint = NoirInk,
                modifier = Modifier.size(26.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(NoirBgDeep)
                    .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                    .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                    .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                style = NoirType.num.copy(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
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
                text = stringResource(R.string.quest_gift_box_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(opening.subtitleRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (opening !is HomeGiftBoxOpeningState.Opening) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.quest_close))
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
                    text = stringResource(R.string.quest_opening_in_progress),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        is HomeGiftBoxOpeningState.Opened ->
            RewardContent(opening = opening)
        is HomeGiftBoxOpeningState.Failed ->
            Text(
                text =
                    when (val reason = opening.reason) {
                        HomeGiftBoxFailure.NoBoxes -> stringResource(R.string.quest_error_no_boxes)
                        is HomeGiftBoxFailure.Unexpected ->
                            reason.detail ?: stringResource(R.string.quest_error_box_generic)
                    },
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
                text = rewardText(opening.reward),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = stringResource(R.string.quest_boxes_left, opening.remainingBoxCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!opening.profileSynced) {
            Text(
                text = stringResource(R.string.quest_profile_sync_note),
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
                text = stringResource(R.string.quest_request_sent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        is HomeGiftBoxOpeningState.Opened ->
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.quest_claim_reward))
            }
        is HomeGiftBoxOpeningState.Failed ->
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.quest_close))
            }
    }
}

private fun HomeGiftBoxOpeningState.subtitleRes(): Int =
    when (this) {
        is HomeGiftBoxOpeningState.Opening -> R.string.quest_subtitle_shake
        is HomeGiftBoxOpeningState.Opened -> R.string.quest_subtitle_reward_credited
        is HomeGiftBoxOpeningState.Failed -> R.string.quest_subtitle_failed
    }

@Composable
private fun rewardText(reward: GiftBoxReward): String =
    when (reward) {
        is GiftBoxReward.Nolics -> stringResource(R.string.quest_reward_nolics, reward.amount)
        is GiftBoxReward.Gold -> stringResource(R.string.quest_reward_gold, reward.amount)
        is GiftBoxReward.Premium ->
            stringResource(R.string.quest_reward_premium_days, reward.premiumDays())
        is GiftBoxReward.Logo -> stringResource(R.string.quest_reward_logo, reward.itemName)
        is GiftBoxReward.Trophy -> stringResource(R.string.quest_reward_trophies, reward.amount)
    }

private fun GiftBoxReward.Premium.premiumDays(): Long = (amount / SECONDS_IN_DAY).coerceAtLeast(1L)

private fun GiftBoxReward.icon(): ImageVector =
    when (this) {
        is GiftBoxReward.Nolics -> Icons.Default.Tag
        is GiftBoxReward.Gold -> Icons.Default.Diamond
        is GiftBoxReward.Premium -> Icons.Default.WorkspacePremium
        is GiftBoxReward.Logo -> Icons.Default.Star
        is GiftBoxReward.Trophy -> Icons.Default.EmojiEvents
    }

private const val SECONDS_IN_DAY = 86_400L
private const val SCRIM_ALPHA = 0.54f
