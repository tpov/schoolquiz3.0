@file:Suppress("MagicNumber", "FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.internet.profile.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassFill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGold
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirOutline
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeLg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeMd
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapePill
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirSuccess
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTOff
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.ProfileUiState
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The badges a gift box can drop, in the order the shelf shows them.
 *
 * Mirrors GIFT_BOX_TROPHY_NAMES in functions/trophies.js. The verification tick is deliberately not
 * here: it is granted by a human decision, not by a box, and it appears beside the name instead.
 */
private val GIFT_BOX_TROPHIES =
    listOf(
        "first_steps",
        "night_owl",
        "early_bird",
        "quick_wit",
        "steady_hand",
        "collector",
        "explorer",
        "polyglot",
    )

/** The six roles, in the order [radarPoint] walks them: top, then clockwise. */
private val QUALIFICATION_AXES = listOf("Спонсор", "Тестер", "Перевод", "Модер", "Админ", "Разраб")

// ─── Identity ───────────────────────────────────────────────────────────────

/**
 * Avatar, name, status — and the rename field in the name's own place.
 *
 * The field replaces the name rather than sitting under it, so the screen does not jump and it is
 * obvious which text is being changed.
 */
@Composable
internal fun ProfileIdentityRow(
    state: ProfileUiState,
    onNicknameChange: (String) -> Unit,
    onStartRename: () -> Unit,
    onCancelRename: () -> Unit,
    onSaveNickname: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalNoirAccent.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(accent.copy(alpha = 0.10f), CircleShape)
                .border(1.dp, NoirGlassStroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(NoirIcons.Users, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (state.isEditingNickname) {
                BasicTextField(
                    value = state.nicknameInput,
                    onValueChange = onNicknameChange,
                    singleLine = true,
                    enabled = !state.isSaving,
                    textStyle = NoirType.rowTitle.copy(fontSize = 16.sp),
                    cursorBrush = SolidColor(accent),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(NoirShapeMd)
                            .background(NoirS1)
                            .border(1.dp, NoirOutline, NoirShapeMd)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = state.profile.nickname,
                        style = NoirType.rowTitle.copy(fontSize = 16.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (state.profile.isVerified) {
                        // The tick a human granted. Accent, never gold: gold in this palette means
                        // something bought or won, and this cannot be either.
                        Icon(
                            NoirIcons.Check,
                            contentDescription = "Подтверждённый аккаунт",
                            tint = accent,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(state.profile.status.displayName.uppercase(), style = NoirType.kicker)
                if (state.profile.premiumUntilMs > 0L) {
                    Text("PREMIUM", style = NoirType.kicker.copy(color = NoirGold))
                }
            }
        }

        when {
            state.isEditingNickname ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NoirTextAction("Отмена", enabled = !state.isSaving, muted = true, onClick = onCancelRename)
                    NoirTextAction("Сохранить", enabled = state.canSaveNickname, onClick = onSaveNickname)
                }

            state.canEditNickname -> NoirTextAction("Изменить", onClick = onStartRename)
        }
    }
}

/** A word that acts. NOIR has no filled buttons in a content row — the label is the control. */
@Composable
private fun NoirTextAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = LocalNoirAccent.current
    Text(
        text = label.uppercase(),
        style =
            NoirType.button.copy(
                color =
                    when {
                        !enabled -> NoirTOff
                        muted -> NoirT3
                        else -> accent
                    },
            ),
        modifier =
            modifier
                .clip(NoirShapeMd)
                // 44dp of touch under a label that only occupies its own line height.
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 14.dp),
    )
}

// ─── League band ────────────────────────────────────────────────────────────

/**
 * League, progress, and the fortnight behind it.
 *
 * The one accent-tinted panel on the screen. It is the answer to "how am I doing", so it gets the
 * colour and everything below it stays neutral.
 */
@Composable
internal fun ProfileLeagueBand(
    leagueName: String,
    nextLeagueName: String?,
    nextMilestoneDelta: Int,
    skillPoints: Int,
    progress: Int,
    activity: List<Int>,
    modifier: Modifier = Modifier,
) {
    val accent = LocalNoirAccent.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(NoirShapeLg)
            .background(accent.copy(alpha = 0.06f))
            .border(1.dp, accent.copy(alpha = 0.32f), NoirShapeLg),
    ) {
        Row(Modifier.fillMaxWidth().height(96.dp)) {
            Column(
                Modifier.weight(1.6f).padding(start = 16.dp, end = 8.dp, top = 15.dp, bottom = 15.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = leagueName.uppercase(),
                    style = NoirType.appbar.copy(fontSize = 26.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 13.dp)
                        .height(4.dp)
                        .clip(NoirShapePill)
                        .background(NoirS2),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                            .height(4.dp)
                            .clip(NoirShapePill)
                            .background(accent),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text =
                            if (nextLeagueName != null) {
                                "$nextMilestoneDelta XP до «$nextLeagueName»"
                            } else {
                                "Высшая лига"
                            },
                        style = NoirType.kicker.copy(color = NoirT3),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("$skillPoints XP", style = NoirType.kicker)
                }
            }
            // Bleeds to the card's edge on purpose: it is a texture behind the panel, not a chart
            // somebody is meant to read values off.
            ActivitySparkline(
                activity = activity,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
    }
}

/**
 * Fourteen days of finished lessons as one line.
 *
 * A fortnight with nothing in it gets words, not a flat line: a line pinned to the floor looks
 * like a chart that failed to load, and the two states have to be told apart at a glance.
 */
@Composable
private fun ActivitySparkline(
    activity: List<Int>,
    modifier: Modifier = Modifier,
) {
    val played = activity.sum()
    if (played == 0) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "НЕТ АКТИВНОСТИ",
                style = NoirType.kicker.copy(color = NoirTOff),
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().semantics {
                contentDescription = "Активность за 14 дней: $played уроков"
            },
        ) {
            if (activity.size < 2) return@Canvas
            val peak = activity.max().coerceAtLeast(1)
            val step = size.width / (activity.size - 1)
            // A quiet day sits on the floor rather than at the very bottom edge, so a flat
            // fortnight still draws a line instead of disappearing into the border.
            val top = size.height * 0.16f
            val floor = size.height * 0.78f
            val points =
                activity.mapIndexed { index, value ->
                    Offset(index * step, floor - (value.toFloat() / peak) * (floor - top))
                }

            val line =
                Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
            val area =
                Path().apply {
                    addPath(line)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
            drawPath(area, color = NoirSuccess.copy(alpha = 0.14f))
            drawPath(
                line,
                color = NoirSuccess,
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        Text(
            "Активность",
            style = NoirType.kicker.copy(color = LocalNoirAccent.current),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 7.dp),
        )
    }
}

// ─── Qualification radar ────────────────────────────────────────────────────

/**
 * The six roles on one shape.
 *
 * Roles nobody has granted sit at zero, which reads as a dent in the outline — that is the point:
 * the shape shows what the account is trusted with, and where it is not.
 */
@Composable
internal fun ProfileQualificationCard(
    values: List<Float>,
    averagePercent: Int,
    rolesHeld: Int,
    topRole: String?,
    modifier: Modifier = Modifier,
) {
    NoirPanel(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Квалификации", style = NoirType.groupTitle)
            Text(
                "СРЕДН. $averagePercent%",
                style = NoirType.kicker.copy(color = NoirSuccess),
            )
        }

        Box(Modifier.fillMaxWidth().height(212.dp), contentAlignment = Alignment.Center) {
            QualificationRadar(values)
            RadarAxisLabel(QUALIFICATION_AXES[0], values[0], Alignment.TopCenter)
            RadarAxisLabel(QUALIFICATION_AXES[1], values[1], Alignment.TopEnd)
            RadarAxisLabel(QUALIFICATION_AXES[2], values[2], Alignment.BottomEnd)
            RadarAxisLabel(QUALIFICATION_AXES[3], values[3], Alignment.BottomCenter)
            RadarAxisLabel(QUALIFICATION_AXES[4], values[4], Alignment.BottomStart)
            RadarAxisLabel(QUALIFICATION_AXES[5], values[5], Alignment.TopStart)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(NoirHair))
        Row(
            Modifier.fillMaxWidth().padding(top = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("$rolesHeld", style = NoirType.num.copy(fontSize = 15.sp, color = NoirT1))
                Text("ИЗ 6 КВАЛИФИКАЦИЙ", style = NoirType.kicker)
            }
            Text(
                text = topRole?.uppercase() ?: "НЕТ",
                style =
                    NoirType.kicker.copy(
                        color = if (topRole != null) LocalNoirAccent.current else NoirTOff,
                    ),
            )
        }
    }
}

@Composable
private fun QualificationRadar(values: List<Float>) {
    Canvas(
        Modifier
            .size(178.dp)
            .semantics { contentDescription = "Радар квалификаций" },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.46f
        repeat(2) { ring ->
            drawRadarWeb(
                axes = values.size,
                center = center,
                radius = radius * ((ring + 1) / 2f),
                color = NoirGlassStroke,
                width = 1.dp.toPx(),
            )
        }
        repeat(values.size) { index ->
            drawLine(
                NoirHair,
                center,
                radarPoint(center, radius, index, values.size, 1f),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawRadarPolygon(values = values, center = center, radius = radius, color = NoirSuccess)
    }
}

/** Role above, level below — the level is the number, so it gets the mono face and the colour. */
@Composable
private fun BoxScope.RadarAxisLabel(
    role: String,
    value: Float,
    alignment: Alignment,
) {
    val level = (value * 100).toInt()
    val dim = level == 0
    Column(
        modifier = Modifier.align(alignment).width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            role.uppercase(),
            style = NoirType.chip.copy(color = if (dim) NoirOutline else NoirT3),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            "$level",
            style =
                NoirType.num.copy(
                    fontSize = 12.sp,
                    color = if (dim) NoirOutline else NoirSuccess,
                ),
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Owned names ────────────────────────────────────────────────────────────

/**
 * The names this account holds, and which one it wears.
 *
 * Lives on the profile even though names are traded in the shop: buying one is shopping, but
 * choosing which one people see is part of who the account is. Tapping a row wears it.
 */
@Composable
internal fun ProfileNicknameShelf(
    state: ProfileUiState,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NoirPanel(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Мои имена", style = NoirType.groupTitle)
            Text(
                text = if (state.nicknamesUnreachable) "—" else "${state.ownedNicknames.size}",
                style = NoirType.kicker.copy(color = LocalNoirAccent.current),
            )
        }

        when {
            state.isLoadingNicknames && state.ownedNicknames.isEmpty() ->
                NicknameNote("Загрузка…", NoirTOff)

            state.nicknamesUnreachable && state.ownedNicknames.isEmpty() ->
                NicknameNote("Не удалось загрузить — нет связи с сервером", NoirT3)

            state.ownedNicknames.isEmpty() ->
                NicknameNote("Пока только текущее имя. Купить ещё — в магазине, вкладка NFT", NoirT3)

            else ->
                state.ownedNicknames.forEach { owned ->
                    NicknameRow(
                        owned = owned,
                        busy = state.switchingNickname == owned.nickname,
                        onSelect = { onSelect(owned.nickname) },
                    )
                }
        }
    }
}

@Composable
private fun NicknameRow(
    owned: OwnedNickname,
    busy: Boolean,
    onSelect: () -> Unit,
) {
    val accent = LocalNoirAccent.current
    // The worn name is not a button: tapping it would promise a change and do nothing.
    val selectable = !owned.active && !busy
    Row(
        Modifier
            .fillMaxWidth()
            .clip(NoirShapeMd)
            .then(if (selectable) Modifier.clickable(onClick = onSelect) else Modifier)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                owned.nickname,
                style = NoirType.rowTitle.copy(color = if (owned.active) NoirT1 else NoirT3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (owned.isForSale) {
                Text(
                    "ПРОДАЁТСЯ ЗА ${owned.listedPrice}",
                    style = NoirType.kicker.copy(color = NoirGold),
                )
            }
        }
        when {
            owned.active ->
                Icon(
                    NoirIcons.Check,
                    contentDescription = "Активное имя",
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )

            busy -> Text("…", style = NoirType.kicker.copy(color = NoirTOff))
            else -> Text("НАДЕТЬ", style = NoirType.kicker.copy(color = accent))
        }
    }
}

@Composable
private fun NicknameNote(
    text: String,
    tone: Color,
) {
    Text(text, style = NoirType.rowSub.copy(fontSize = 11.sp, color = tone))
}

// ─── Trophy shelf ───────────────────────────────────────────────────────────

/**
 * Eight slots, filled or not.
 *
 * Empty slots are drawn rather than omitted: six badges in a row look like a complete set, and the
 * whole point of a collection is seeing what is still missing.
 */
@Composable
internal fun ProfileTrophyShelf(
    profile: UserProfile,
    modifier: Modifier = Modifier,
) {
    val held = profile.trophies
    val earned = GIFT_BOX_TROPHIES.count { it in held }
    NoirPanel(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Трофеи", style = NoirType.groupTitle)
            Text(
                "$earned ИЗ ${GIFT_BOX_TROPHIES.size}",
                style = NoirType.kicker.copy(color = NoirGold),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GIFT_BOX_TROPHIES.forEach { name ->
                TrophySlot(filled = name in held, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrophySlot(
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (filled) NoirGold.copy(alpha = 0.10f) else NoirGlassFill)
            .border(1.dp, if (filled) NoirGold.copy(alpha = 0.34f) else NoirHair, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = NoirIcons.Trophy,
            contentDescription = null,
            tint = if (filled) NoirGold else NoirS2,
            modifier = Modifier.size(15.dp),
        )
    }
}

// ─── Footer figures ─────────────────────────────────────────────────────────

/**
 * The counts nobody opens the screen for.
 *
 * Hairline rows rather than cards: giving each its own panel would make the page longer without
 * making it say more.
 */
@Composable
internal fun ProfileFooterRows(
    profile: UserProfile,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        FooterRow("Коробки", profile.boxCount.toString())
        FooterRow("Серия", "${profile.boxStreakDays} дн.")
        FooterRow("Логотипы", profile.ownedLogos.size.toString())
        FooterRow(
            label = "Языки",
            value = profile.knownLanguages.joinToString(" ") { it.uppercase() }.ifBlank { "—" },
        )
    }
}

@Composable
private fun FooterRow(
    label: String,
    value: String,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(NoirHair))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label.uppercase(), style = NoirType.kicker)
            Text(value, style = NoirType.num.copy(fontSize = 12.sp, color = NoirT1))
        }
    }
}

// ─── Shared shell ───────────────────────────────────────────────────────────

/** The neutral panel the lower half of the screen is built from. */
@Composable
private fun NoirPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(NoirShapeLg)
            .background(NoirGlassFill)
            .border(1.dp, NoirHair, NoirShapeLg)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** A message that appears, says one thing and goes away again. */
@Composable
internal fun ProfileToast(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(NoirShapeMd)
            .background(NoirS1)
            .border(1.dp, NoirGlassStroke, NoirShapeMd)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(message.uppercase(), style = NoirType.kicker.copy(color = NoirT1))
    }
}

// ─── Radar geometry ─────────────────────────────────────────────────────────

private fun DrawScope.drawRadarWeb(
    axes: Int,
    center: Offset,
    radius: Float,
    color: Color,
    width: Float,
) {
    val path = Path()
    repeat(axes) { index ->
        val point = radarPoint(center, radius, index, axes, 1f)
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color = color, style = Stroke(width = width))
}

private fun DrawScope.drawRadarPolygon(
    values: List<Float>,
    center: Offset,
    radius: Float,
    color: Color,
) {
    val points =
        values.mapIndexed { index, value ->
            radarPoint(center, radius, index, values.size, value.coerceIn(0f, 1f))
        }
    val path = Path()
    points.forEachIndexed { index, point ->
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path = path, color = color.copy(alpha = 0.12f))
    drawPath(path = path, color = color, style = Stroke(width = 1.8.dp.toPx()))
    // Only the corners that carry a level get a dot; a dot at the centre would read as a value.
    points.forEachIndexed { index, point ->
        if (values[index] > 0f) drawCircle(color = color, radius = 2.6.dp.toPx(), center = point)
    }
}

/** Axis 0 points straight up, and the rest run clockwise from it. */
private fun radarPoint(
    center: Offset,
    radius: Float,
    index: Int,
    count: Int,
    value: Float,
): Offset {
    val angle = -PI / 2.0 + (2.0 * PI * index / count)
    return Offset(
        x = center.x + (cos(angle) * radius * value).toFloat(),
        y = center.y + (sin(angle) * radius * value).toFloat(),
    )
}

/**
 * The one offer an anonymous account gets.
 *
 * Says what is gained rather than what is required: signing in is what unlocks verification and
 * trading, and it keeps everything already earned — which is the part somebody hesitating over
 * this button actually wants to know.
 */
@Composable
internal fun ProfileGoogleUpgrade(
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalNoirAccent.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(NoirShapeLg)
            .background(accent.copy(alpha = 0.06f))
            .border(1.dp, accent.copy(alpha = 0.32f), NoirShapeLg)
            .then(if (busy) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ВОЙТИ ЧЕРЕЗ GOOGLE",
                style = NoirType.button.copy(color = if (busy) NoirTOff else accent),
                modifier = Modifier.weight(1f),
            )
            if (busy) Text("…", style = NoirType.kicker.copy(color = NoirTOff))
        }
        Text(
            "Прогресс гостя сохранится. Регистрация открывает подтверждение аккаунта и торговлю именами.",
            style = NoirType.rowSub.copy(fontSize = 11.sp, color = NoirT3),
        )
    }
}
