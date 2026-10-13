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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButtonStyle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirDanger
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassCard
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIconButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirWashHome
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
    Box(modifier = modifier.fillMaxSize().noirScreenWash(NoirWashHome)) {
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
                    style = NoirType.num.copy(fontSize = 10.5.sp, color = NoirT2),
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
                    style = NoirType.rowSub.copy(color = NoirT2),
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
        CircularProgressIndicator(color = LocalNoirAccent.current)
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
    Box(
        modifier =
            modifier
                .size(56.dp)
                // The manage FAB shares the gift FAB's graphite plate: a corner button on Home is
                // furniture, and the one accent on this screen already belongs to the continue
                // card's kicker.
                .clip(CircleShape)
                .background(NoirFabPlate)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = NoirIcons.Plus,
            contentDescription = stringResource(R.string.quest_cd_add_quest),
            tint = NoirT1,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun GiftBoxFab(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    // GiftBoxModal: a 56dp round plate in the gift graphite (#383838), a 28dp
                    // icon. The accent version read as a primary action; a box waiting to be
                    // opened is not one.
                    .shadow(elevation = 8.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(NoirFabPlate)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = NoirIcons.Box,
                contentDescription = stringResource(R.string.quest_cd_open_box),
                tint = NoirT1,
                modifier = Modifier.size(28.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(NoirBgDeep)
                    .border(1.dp, NoirGlassStroke, RoundedCornerShape(999.dp))
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                style = NoirType.num.copy(color = NoirT1, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** The gift FAB's graphite plate from the accepted gift-box drawing. */
private val NoirFabPlate = Color(0xFF383838)

@Composable
private fun GiftBoxOpeningOverlay(
    opening: HomeGiftBoxOpeningState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        // The drawer's scrim: black 66% over the accepted Home, per GiftBoxModal.
        modifier = modifier.background(Color.Black.copy(alpha = SCRIM_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .clip(NoirShapeGift)
                    .background(NoirS1)
                    .border(1.dp, NoirGlassStroke, NoirShapeGift)
                    .padding(24.dp),
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

/** GiftBoxModal's own radius: 22, between the card's 16 and the dialog's 20. */
private val NoirShapeGift = RoundedCornerShape(22.dp)

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
                style = NoirType.groupTitle.copy(fontSize = 20.sp),
                color = NoirT1,
            )
            Text(
                text = stringResource(opening.subtitleRes()),
                style = NoirType.rowSub,
                color = NoirT2,
            )
        }
        if (opening !is HomeGiftBoxOpeningState.Opening) {
            NoirIconButton(
                icon = NoirIcons.Close,
                contentDescription = stringResource(R.string.quest_close),
                onClick = onDismiss,
            )
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

    Box(
        modifier =
            Modifier
                .size(152.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(NoirS2)
                .border(1.dp, NoirGlassStroke, RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = NoirIcons.Box,
            contentDescription = null,
            modifier =
                Modifier
                    .size(112.dp)
                    .rotate(if (isOpening) shake else 0f)
                    .scale(boxScale),
            tint = NoirT1,
        )
    }
}

@Composable
private fun GiftBoxOverlayBody(opening: HomeGiftBoxOpeningState) {
    when (opening) {
        is HomeGiftBoxOpeningState.Opening ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = LocalNoirAccent.current,
                )
                Text(
                    text = stringResource(R.string.quest_opening_in_progress),
                    modifier = Modifier.padding(top = 8.dp),
                    style = NoirType.rowSub,
                    color = NoirT2,
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
                style = NoirType.rowSub,
                color = NoirDanger,
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
            val (icon, tint) = opening.reward.icon()
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = tint,
            )
            Text(
                text = rewardText(opening.reward),
                style = NoirType.num.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = NoirT1,
            )
        }
        Text(
            text = stringResource(R.string.quest_boxes_left, opening.remainingBoxCount),
            style = NoirType.rowSub,
            color = NoirT2,
        )
        if (!opening.profileSynced) {
            Text(
                text = stringResource(R.string.quest_profile_sync_note),
                style = NoirType.rowSub.copy(fontSize = 12.sp, color = NoirT3),
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
                style = NoirType.kicker,
                color = NoirT3,
            )
        is HomeGiftBoxOpeningState.Opened ->
            NoirButton(text = stringResource(R.string.quest_claim_reward), onClick = onDismiss)
        is HomeGiftBoxOpeningState.Failed ->
            NoirButton(
                text = stringResource(R.string.quest_close),
                onClick = onDismiss,
                style = NoirButtonStyle.Ghost,
            )
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

/**
 * The currency carries the icon and the tint: nolics grey, gold and premium gold, a worn logo
 * neutral, a trophy gold. Material's default set said none of that.
 */
private fun GiftBoxReward.icon(): Pair<ImageVector, Color> =
    when (this) {
        is GiftBoxReward.Nolics -> NoirIcons.Nolic to NoirT3
        is GiftBoxReward.Gold -> NoirIcons.GoldStack to NoirGold
        is GiftBoxReward.Premium -> NoirIcons.Gem to NoirGold
        is GiftBoxReward.Logo -> NoirIcons.Star to NoirT2
        is GiftBoxReward.Trophy -> NoirIcons.Trophy to NoirGold
    }

private const val SECONDS_IN_DAY = 86_400L

/** GiftBoxModal: the Home scrim is black 66%. */
private const val SCRIM_ALPHA = 0.66f
