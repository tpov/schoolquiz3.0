@file:Suppress("MagicNumber", "FunctionNaming", "ktlint:standard:function-naming")
// Composables are PascalCase by convention; suppressed once here rather than on each of the
// fourteen components below.

package com.tpov.schoolquiz.android.core.designsystem.noir

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ─── App bar ────────────────────────────────────────────────────────────────

/** Icon button at 44dp — the minimum touch target, kept visible rather than implied. */
@Composable
fun NoirIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NoirT2,
) {
    Box(
        modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun NoirAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            // Android 15 and up enforce edge-to-edge for apps targeting SDK 35, so a bar that does
            // not inset itself is drawn beneath the clock and the cutout. Material's TopAppBar did
            // this for us; this one has to do it for itself.
            .statusBarsPadding()
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A screen either goes back or opens something; `leading` covers the second case, which is
        // what a root screen with a drawer needs.
        if (leading != null) {
            leading()
            Spacer(Modifier.width(4.dp))
        } else if (onBack != null) {
            NoirIconButton(NoirIcons.Back, contentDescription = "Назад", onClick = onBack)
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title.uppercase(), style = NoirType.appbar)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

// ─── Screen ground ─────────────────────────────────────────────────────────

/** How far the mode tint rises out of the black at mid-screen. */
const val NOIR_BACKGROUND_ACCENT_ALPHA = 0.08f

/**
 * The ground every screen sits on: black at both edges, the mode colour surfacing in the middle.
 *
 * It carries which mode you are in without a label — gold in the shop, the round's colour while
 * playing. Kept to 8% because it has to lose every contest with the content in front of it; the
 * moment it competes it stops being a ground.
 */
@Composable
fun Modifier.noirScreenGround(accent: Color = LocalNoirAccent.current): Modifier =
    this.background(
        Brush.verticalGradient(
            0f to NoirBg,
            0.5f to accent.copy(alpha = NOIR_BACKGROUND_ACCENT_ALPHA).compositeOver(NoirBg),
            1f to NoirBg,
        ),
    )

/** A balance, as a pill: the icon carries the currency, the number stays white. */
@Composable
fun NoirBalancePill(
    icon: ImageVector,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(NoirShapePill)
            .background(NoirS1)
            .border(1.dp, Color(0x12FFFFFF), NoirShapePill)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Text(value, style = NoirType.num.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold))
    }
}

/**
 * A section rule: a mono kicker, a hairline that runs to the count on the right.
 *
 * Used where a group needs naming without a heavy header — the beta shelf below the shop, for one.
 */
@Composable
fun NoirSectionRule(
    label: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label.uppercase(), style = NoirType.kicker)
        Box(Modifier.weight(1f).height(1.dp).background(NoirHair))
        if (trailing != null) Text(trailing, style = NoirType.kicker)
    }
}

// ─── Glass card ────────────────────────────────────────────────────────────

/** Matte glass: a white wash so faint it reads as a lit surface rather than a lighter grey. */
val NoirGlassFill = Color(0x09FFFFFF)
val NoirGlassStroke = Color(0x17FFFFFF)

/**
 * The card the screens are built from.
 *
 * Distinct from [NoirGroup], and both are needed. A group draws structure with hairlines and holds
 * rows; this is a single object that has to lift off the black on its own — a shop item, a result,
 * a lesson to continue. On a pure black ground the lift cannot come from shadow, so it comes from a
 * wash of white behind a slightly brighter edge.
 */
@Composable
fun NoirGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = NoirShapeLg,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(NoirGlassFill)
            .border(1.dp, NoirGlassStroke, shape)
            // 82dp is the specified minimum, and it also clears the 44dp touch target twice over.
            .defaultMinSize(minHeight = 82.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * The 34dp tile an item leads with.
 *
 * Tinted by role rather than filled: the tint carries the meaning and the icon stays monoline, so a
 * row of them does not turn into a row of coloured blocks.
 */
@Composable
fun NoirItemTile(
    icon: ImageVector,
    tint: Color = LocalNoirAccent.current,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(34.dp)
            .clip(NoirShapeMd)
            .background(tint.copy(alpha = 0.09f))
            .border(1.dp, tint.copy(alpha = 0.22f), NoirShapeMd),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

// ─── Group: a hairline block, not a floating card ───────────────────────────

/**
 * The base container. Surface [NoirS1], a hairline outline and
 * a light top edge. Shadow does nothing on black, so elevation is carried by the surface itself.
 * For Pro or seasonal blocks pass `Modifier.fxGold()` or `.fxViolet()` with `hairline = false`,
 * since those effects draw their own outline.
 */
@Composable
fun NoirGroup(
    modifier: Modifier = Modifier,
    hairline: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(NoirShapeLg)
            .background(NoirS1)
            .drawBehind {
                // 4.5% top edge: the milled lip that makes a surface read as raised
                drawLine(
                    color = Color.White.copy(alpha = 0.045f),
                    start = Offset(0f, 0.5.dp.toPx()),
                    end = Offset(size.width, 0.5.dp.toPx()),
                )
            }
            .then(if (hairline) Modifier.border(1.dp, NoirHair, NoirShapeLg) else Modifier)
            .padding(bottom = 0.dp),
        content = content,
    )
}

/** Group header: a mono kicker on the left, a free slot on the right. */
@Composable
fun NoirGroupHeader(
    label: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = NoirHair,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = NoirType.kicker)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/**
 * List row. At least 56dp tall, with a hairline below it — pass `showDivider = false` on the last.
 * Supplying `onClick` adds the ripple and the Button role.
 */
@Composable
fun NoirRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    leading: @Composable (RowScope.() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .then(
                if (showDivider) {
                    Modifier.drawBehind {
                        drawLine(
                            color = NoirHair,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke(this)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) { content() }
        trailing?.invoke(this)
    }
}

/** The 34×34 icon tile that opens a row. */
@Composable
fun NoirRowIcon(
    icon: ImageVector,
    tint: Color = NoirT2,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(NoirShapeMd)
            .background(NoirS3),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

// ─── Switch ─────────────────────────────────────────────────────────────────

/** Track 48×28, thumb 22. On: the skin accent with an ink thumb. */
@Composable
fun NoirSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val accent = LocalNoirAccent.current
    val track by animateColorAsState(
        targetValue = if (checked) accent else NoirS4,
        animationSpec = tween(180),
        label = "track",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) NoirInk else NoirT3,
        animationSpec = tween(180),
        label = "thumbColor",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = tween(180),
        label = "thumb",
    )
    Box(
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(CircleShape)
                .background(track)
                .then(
                    if (!checked) Modifier.border(1.dp, NoirOutline, CircleShape) else Modifier,
                ),
        ) {
            Box(
                Modifier
                    .padding(start = thumbOffset, top = 3.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
    }
}

// ─── Buttons: one action, one primary ───────────────────────────────────────

enum class NoirButtonStyle { Primary, Ghost, Gold }

/**
 * Primary is the accent fill and there is one per screen. Ghost is secondary, gold is a Pro action.
 * Pressed lightens the fill with a 14% white veil; the label never dims, because dimming text on
 * press reads as the control going away. Focus draws a 2dp accent ring. Disabled is the only
 * state allowed to drop below the contrast floor.
 */
@Composable
fun NoirButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NoirButtonStyle = NoirButtonStyle.Primary,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
) {
    val accent = LocalNoirAccent.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()

    val bg: Color
    val fg: Color
    val stroke: Color?
    when (style) {
        NoirButtonStyle.Primary -> {
            bg = accent
            fg = NoirInk
            stroke = null
        }
        NoirButtonStyle.Ghost -> {
            bg = NoirS3
            fg = NoirT1
            stroke = NoirOutline
        }
        NoirButtonStyle.Gold -> {
            bg = Color.Transparent
            fg = NoirGold
            stroke = NoirGold.copy(alpha = 0.52f)
        }
    }
    val bgFinal =
        if (!enabled) {
            NoirS2
        } else if (pressed) {
            Color.White.copy(alpha = 0.14f).compositeOver(bg)
        } else {
            bg
        }
    val fgFinal = if (enabled) fg else NoirTOff
    val strokeFinal =
        when {
            !enabled -> NoirHair
            pressed && style == NoirButtonStyle.Ghost -> NoirOutline2
            pressed && style == NoirButtonStyle.Gold -> NoirGold
            else -> stroke
        }

    Box(
        modifier
            .then(
                if (focused && enabled) {
                    Modifier
                        .border(2.dp, accent, NoirShapeMd)
                        .padding(2.dp)
                } else {
                    Modifier.padding(2.dp)
                },
            )
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(NoirShapeMd)
            .background(bgFinal)
            .then(
                if (strokeFinal != null) {
                    Modifier.border(1.dp, strokeFinal, NoirShapeMd)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = NoirType.button, color = fgFinal)
    }
}

// ─── Chips and statuses ─────────────────────────────────────────────────────

enum class NoirChipTone { Neutral, Accent, Gold, Violet, Ok, Danger, Off }

@Composable
fun NoirChip(
    text: String,
    tone: NoirChipTone = NoirChipTone.Neutral,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
) {
    val color =
        when (tone) {
            NoirChipTone.Neutral -> NoirT2
            NoirChipTone.Accent -> LocalNoirAccent.current
            NoirChipTone.Gold -> NoirGold
            NoirChipTone.Violet -> NoirViolet
            NoirChipTone.Ok -> NoirSuccess
            NoirChipTone.Danger -> NoirDanger
            NoirChipTone.Off -> NoirTOff
        }
    Row(
        modifier
            .clip(CircleShape)
            .background(
                if (tone == NoirChipTone.Neutral) {
                    NoirS2
                } else {
                    color.copy(alpha = 0.10f)
                },
            )
            .border(
                1.dp,
                if (tone == NoirChipTone.Neutral) {
                    NoirOutline
                } else {
                    color.copy(alpha = 0.45f)
                },
                CircleShape,
            )
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint ?: color,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(text.uppercase(), style = NoirType.chip, color = color)
    }
}

// ─── Progress ───────────────────────────────────────────────────────────────

/** A thin filled bar, not an outlined one. */
@Composable
fun NoirProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalNoirAccent.current,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(NoirS4),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(color),
        )
    }
}

// ─── Bottom navigation ──────────────────────────────────────────────────────

/** Width of the active tab's accent rail, as a fraction of the slot (the canvas uses 52%). */
private const val ACTIVE_RAIL_FRACTION = 0.52f

/**
 * One destination in the bottom bar.
 *
 * The component used to hold its own list of tabs, which meant the design system decided what an
 * app navigates to. It does not: the app passes its own.
 */
data class NoirNavItem(val label: String, val icon: ImageVector)

@Composable
fun NoirBottomNav(
    items: List<NoirNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(NoirS1.copy(alpha = 0.94f))
            .drawBehind {
                drawLine(
                    color = NoirHair,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                )
            }
            .navigationBarsPadding(),
    ) {
        items.forEachIndexed { index, tab ->
            val active = index == selectedIndex
            val accent = LocalNoirAccent.current
            val color by animateColorAsState(
                targetValue = if (active) accent else NoirT3,
                animationSpec = tween(120),
                label = "nav",
            )
            Column(
                Modifier
                    .weight(1f)
                    .drawBehind {
                        // The active tab carries a short accent rail on the bar's top edge,
                        // like the canvas: 2px tall, about half the slot wide.
                        if (active) {
                            val railWidth = size.width * ACTIVE_RAIL_FRACTION
                            drawRect(
                                color = accent,
                                topLeft = Offset((size.width - railWidth) / 2f, 0f),
                                size = Size(railWidth, 2.dp.toPx()),
                            )
                        }
                    }
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    tab.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    tab.label.uppercase(),
                    style =
                        NoirType.navLabel.copy(
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        ),
                    color = color,
                )
            }
        }
    }
}

// ─── Toast: success and failure ─────────────────────────────────────────────

class NoirToastState {
    var message by mutableStateOf<String?>(null)
        private set
    var ok by mutableStateOf(true)
        private set

    suspend fun show(
        text: String,
        success: Boolean = true,
    ) {
        message = text
        ok = success
        delay(2400)
        message = null
    }
}

@Composable
fun rememberNoirToast(): NoirToastState = remember { NoirToastState() }

/** Place in the screen's Box with `Modifier.align(Alignment.BottomCenter)`. */
@Composable
fun NoirToastHost(
    state: NoirToastState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.message != null,
        modifier = modifier,
        enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(180)),
    ) {
        Row(
            Modifier
                .padding(bottom = 32.dp)
                .clip(NoirShapeMd)
                .background(NoirS4)
                .border(
                    1.dp,
                    if (state.ok) {
                        NoirOutline
                    } else {
                        NoirDanger.copy(alpha = 0.45f)
                    },
                    NoirShapeMd,
                )
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (state.ok) NoirIcons.Check else NoirIcons.Info,
                contentDescription = null,
                tint = if (state.ok) NoirSuccess else NoirDanger,
                modifier = Modifier.size(16.dp),
            )
            Text(state.message.orEmpty(), fontSize = 13.sp, color = NoirT1)
        }
    }
}

// ─── Skeleton: the loading state ────────────────────────────────────────────

/** Shimmer rows for the loading state. Honours the system animator scale, so it stops when
 *  animations are turned off. */
@Composable
fun NoirSkeletonRows(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skel")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "shift",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NoirSkeletonBox(shift, Modifier.size(40.dp), CircleShape)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoirSkeletonBox(shift, Modifier.fillMaxWidth(0.7f).height(12.dp))
                    NoirSkeletonBox(shift, Modifier.fillMaxWidth(0.45f).height(12.dp))
                }
            }
        }
        NoirSkeletonBox(shift, Modifier.fillMaxWidth().height(48.dp))
    }
}

@Composable
private fun NoirSkeletonBox(
    shift: Float,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape = NoirShapeSm,
) {
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    0f to NoirS2,
                    0.5f to NoirS4,
                    1f to NoirS2,
                    start = Offset(shift * 600f - 300f, 0f),
                    end = Offset(shift * 600f, 0f),
                ),
            ),
    )
}
