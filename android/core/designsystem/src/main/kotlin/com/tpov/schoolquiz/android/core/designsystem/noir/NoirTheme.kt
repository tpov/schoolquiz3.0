@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.core.designsystem.noir

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * NOIR — the design system defined in noir-system.md and drawn in the "NOIR Design System" canvas.
 *
 * This is not a replacement for SchoolQuizTheme. NoirTheme provides CompositionLocals only, so the
 * two nest — SchoolQuizTheme { NoirTheme { … } } — and every screen still on the old components
 * keeps working untouched while they are converted one at a time.
 *
 * Surface values follow the canvas and the specification, which are the fixed choice; the earlier
 * Compose draft used a darker set and is superseded here. The palette is deliberately lighter than
 * a naive "dark theme": on a pure black ground a near-black surface reads as nothing at all.
 */

// ─── Colour tokens ─────────────────────────────────────────────────────────────────────────────

/** Pure OLED black. The brand choice, and not the usual #121212. */
val NoirBg = Color(0xFF000000)

/**
 * A step off pure black — the drawer ground, badges sitting on art, anything that must read as
 * "a layer above" without becoming a surface.
 */
val NoirBgDeep = Color(0xFF08080A)

/** Surface 1 — a card sitting on the black. */
val NoirS1 = Color(0xFF1E1E24)

/** Surface 2 — bars, input fields, chips. */
val NoirS2 = Color(0xFF26262E)

/** Surface 3 — a raised card. */
val NoirS3 = Color(0xFF2E2E36)

/** Surface 4 — dialogs and sheets, the highest level. */
val NoirS4 = Color(0xFF383840)

/**
 * Hairline outline.
 *
 * Replaces ADR-0010's #2C2C2C, which sat at roughly 1.1:1 against the surface — a border nobody
 * could see. Structure on black has to come from lines, so the line has to be visible.
 */
val NoirOutline = Color(0xFF44444E)

/** Focus and hover outline. */
val NoirOutline2 = Color(0xFF5E5E6A)

/** Internal divider: 6.5% white. Shadows are invisible on black, so lines do that work. */
val NoirHair = Color(0x10FFFFFF)

/** Headings and numbers. */
val NoirT1 = Color(0xFFFFFFFF)

/** Body text. */
val NoirT2 = Color(0xFFB8B8B8)

/** Secondary text. Replaces #888888, which failed AA at about 3.5:1; this clears 4.5:1. */
val NoirT3 = Color(0xFF9C9CA6)

/** Disabled and placeholder only — the single state allowed to fall below the contrast floor. */
val NoirTOff = Color(0xFF6E6E78)

/** Text and icons drawn on top of a filled accent. */
val NoirInk = Color(0xFF05090F)

/** Role: Pro and premium. Fixed across skins. */
val NoirGold = Color(0xFFFFD700)

/** Role: limited and seasonal. Fixed across skins. */
val NoirViolet = Color(0xFF9680F2)

/** Success, and the easy mode. Lightness aligned with the rest of the family. */
val NoirSuccess = Color(0xFF5CC97A)

/** Error, destructive actions, and the hard mode. */
val NoirDanger = Color(0xFFF0564B)

// ─── Skins ─────────────────────────────────────────────────────────────────────────────────────

/**
 * One skeleton, three accents.
 *
 * Azure is the default: Google Blue with the hue deliberately shifted, so the app stops borrowing
 * somebody else's blue. Amending ADR-0010 is part of adopting this.
 */
enum class NoirSkin(val label: String, val accent: Color) {
    Azure("Азур", Color(0xFF0599EF)),
    Amethyst("Аметист", Color(0xFF9680F2)),
    Teal("Бирюза", Color(0xFF00AFAF)),
}

/** Round mode. Drives the glow behind the question screen. */
enum class NoirMode(val label: String) {
    Arena("Арена"),
    Easy("Лёгкий"),
    Hard("Сложный"),
}

/** Live state. Hold one per application, not per screen. */
class NoirState {
    var skin by mutableStateOf(NoirSkin.Azure)
    var mode by mutableStateOf(NoirMode.Arena)
}

@Composable
fun rememberNoirState(): NoirState = remember { NoirState() }

val LocalNoir = compositionLocalOf<NoirState> { error("NoirTheme is missing above this point") }
val LocalNoirAccent = compositionLocalOf { NoirSkin.Azure.accent }
val LocalNoirDisplay = compositionLocalOf<FontFamily> { FontFamily.Default }
val LocalNoirMono = compositionLocalOf<FontFamily> { FontFamily.Monospace }

/** Glow colour for the current mode: arena takes the skin accent, easy is success, hard is danger. */
@Composable
fun noirGlow(): Color =
    when (LocalNoir.current.mode) {
        NoirMode.Arena -> LocalNoirAccent.current
        NoirMode.Easy -> NoirSuccess
        NoirMode.Hard -> NoirDanger
    }

/**
 * Entry point. Nests inside [com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme].
 *
 * Both fonts default to the bundled cuts — Archivo variable at width 112 for display, JetBrains
 * Mono for anything numeric — so a plain `NoirTheme { … }` already reads as NOIR. Tests can still
 * pass system faces to keep screenshots deterministic.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun NoirTheme(
    state: NoirState = rememberNoirState(),
    displayFont: FontFamily = NoirDisplayFont,
    monoFont: FontFamily = NoirMonoFont,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalNoir provides state,
        LocalNoirAccent provides state.skin.accent,
        LocalNoirDisplay provides displayFont,
        LocalNoirMono provides monoFont,
        content = content,
    )
}

// ─── Typography ────────────────────────────────────────────────────────────────────────────────

/**
 * Wide uppercase for display, monospace for anything numeric.
 *
 * Buttons are mono, never the display face — an explicit rule in the specification, and one of its
 * listed anti-patterns. Numbers use tabular figures so a running timer does not jitter.
 */
object NoirType {
    /** Mono captions, section labels, statuses. Uppercased where used. */
    val kicker: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalNoirMono.current,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.em,
                color = NoirT3,
            )

    /** Prices, timers, counters — always tabular. */
    val num: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalNoirMono.current,
                fontFeatureSettings = "tnum",
                color = NoirT1,
            )

    val button: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalNoirMono.current,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.12.em,
            )

    val chip: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalNoirMono.current,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.12.em,
            )

    val navLabel: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalNoirMono.current,
                fontSize = 9.sp,
                letterSpacing = 0.1.em,
            )

    /** App bar title — wide uppercase. */
    val appbar: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalNoirDisplay.current,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.12.em,
                color = NoirT1,
            )

    val rowTitle: TextStyle
        @Composable get() =
            TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NoirT1)

    val rowSub: TextStyle
        @Composable get() = TextStyle(fontSize = 12.sp, color = NoirT3)

    val timer: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalNoirMono.current,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
                color = NoirT1,
                fontFeatureSettings = "tnum",
            )

    /** Question text — display face, but not uppercased: it has to stay readable. */
    val question: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalNoirDisplay.current,
                fontSize = 28.sp,
                // Design v2: 28px at line-height 1.22, weight 600.
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.01).em,
                color = NoirT1,
            )

    val price: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalNoirMono.current,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = NoirT1,
                fontFeatureSettings = "tnum",
            )

    val refCode: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalNoirMono.current,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.16.em,
                color = NoirT1,
            )

    val groupTitle: TextStyle
        @Composable get() =
            TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NoirT1)
}

// ─── Shapes ────────────────────────────────────────────────────────────────────────────────────

val NoirShapeSm: Shape = RoundedCornerShape(8.dp)

/** Buttons. */
val NoirShapeMd: Shape = RoundedCornerShape(12.dp)

/** Cards and hairline groups. */
val NoirShapeLg: Shape = RoundedCornerShape(16.dp)

/** Dialogs and sheets. The specification says 20; the earlier Compose draft had 24. */
val NoirShapeXl: Shape = RoundedCornerShape(20.dp)

/** Chips and anything pill-shaped. */
val NoirShapePill: Shape = RoundedCornerShape(999.dp)
