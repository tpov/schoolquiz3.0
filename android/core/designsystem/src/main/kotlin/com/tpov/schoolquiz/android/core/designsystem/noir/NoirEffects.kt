@file:Suppress("MagicNumber", "FunctionNaming", "ktlint:standard:function-naming")
// Composables are PascalCase by convention; suppressed once here rather than on each of the
// fourteen components below.

package com.tpov.schoolquiz.android.core.designsystem.noir

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

// ─── Effects: each carries a role, and no more than two appear on a screen ──────────

/**
 * The signature move: the round's mode glowing behind the screen.
 * A 17% radial over pure black with darkened edges, coloured by [noirGlow] — arena takes the
 * accent, easy is success, hard is danger. Switching mode animates over 320ms.
 *
 * Використання: першим елементом у Box екрана гри, `Modifier.matchParentSize()`.
 */
@Composable
fun NoirGlowBed(modifier: Modifier = Modifier) {
    val glow by animateColorAsState(
        targetValue = noirGlow(),
        animationSpec = tween(320),
        label = "glow",
    )
    Box(
        modifier.drawBehind {
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.17f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.52f),
                        radius = size.maxDimension * 0.75f,
                    ),
            )
            drawRect(
                brush =
                    Brush.verticalGradient(
                        0f to NoirBg,
                        0.32f to Color.Transparent,
                        0.68f to Color.Transparent,
                        1f to NoirBg,
                    ),
            )
        },
    )
}

/**
 * Role: Pro and premium. One card per screen.
 * A 55% outline, a 9%-to-transparent gradient, and a sheen every 7 seconds — which is to say it
 * is idle 72% of the time. Constant motion would make it decoration instead of a signal.
 *
 * On the outer halo (22% beyond the card): plain Compose cannot paint outside its layout bounds.
 * On Android it would take a BlurMaskFilter inside drawBehind. Left off for now — the outline and
 * the sheen already read clearly enough to be worth the simpler code.
 *
 * Reduced motion needs no special case: Compose honours ANIMATOR_DURATION_SCALE, so setting it to
 * zero stops rememberInfiniteTransition on its own.
 */
fun Modifier.fxGold(shape: Shape = NoirShapeLg): Modifier =
    composed {
        val transition = rememberInfiniteTransition(label = "goldSheen")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(tween(durationMillis = 7000, easing = LinearEasing)),
            label = "phase",
        )
        this
            .background(
                brush =
                    Brush.verticalGradient(
                        0f to NoirGold.copy(alpha = 0.09f),
                        0.62f to Color.Transparent,
                    ),
                shape = shape,
            )
            .border(1.dp, NoirGold.copy(alpha = 0.55f), shape)
            .drawWithContent {
                drawContent()
                val sweep = (phase - 0.72f) / 0.28f
                if (sweep in 0f..1f) {
                    val w = size.width
                    val cx = lerp(-w * 0.3f, w * 1.3f, sweep)
                    drawRect(
                        brush =
                            Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to NoirGold.copy(alpha = 0.16f),
                                1f to Color.Transparent,
                                start = Offset(cx - w * 0.13f, 0f),
                                end = Offset(cx + w * 0.13f, size.height),
                            ),
                    )
                }
            }
    }

/**
 * Role: seasonal and limited. A 55% outline with a 10% gradient, and no animation.
 * Independent of the skin — always violet.
 */
fun Modifier.fxViolet(shape: Shape = NoirShapeLg): Modifier =
    this
        .background(
            brush =
                Brush.verticalGradient(
                    0f to NoirViolet.copy(alpha = 0.10f),
                    0.62f to Color.Transparent,
                ),
            shape = shape,
        )
        .border(1.dp, NoirViolet.copy(alpha = 0.55f), shape)
