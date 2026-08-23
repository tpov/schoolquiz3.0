@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.core.designsystem.noir

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

// ─── Monoline icons, 24×24, stroke 2 — no external dependency ───────────────
// Drawn white; the real colour comes from tint at the call site.

object NoirIcons {
    val Back: ImageVector =
        stroke {
            moveTo(15f, 18f)
            lineTo(9f, 12f)
            lineTo(15f, 6f)
        }

    val ChevronRight: ImageVector =
        stroke {
            moveTo(9f, 6f)
            lineTo(15f, 12f)
            lineTo(9f, 18f)
        }

    val Search: ImageVector =
        stroke {
            circle(11f, 11f, 7f)
            moveTo(16.5f, 16.5f)
            lineTo(20f, 20f)
        }

    val Close: ImageVector =
        stroke {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }

    val Info: ImageVector =
        stroke {
            circle(12f, 12f, 9f)
            moveTo(12f, 16f)
            lineTo(12f, 12f)
            moveTo(12f, 8.5f)
            lineTo(12f, 9f)
        }

    val Share: ImageVector =
        stroke {
            moveTo(12f, 16f)
            lineTo(12f, 4f)
            moveTo(8f, 8f)
            lineTo(12f, 4f)
            lineTo(16f, 8f)
            moveTo(5f, 14f)
            lineTo(5f, 19f)
            lineTo(19f, 19f)
            lineTo(19f, 14f)
        }

    val Check: ImageVector =
        stroke {
            moveTo(5f, 13f)
            lineTo(9f, 17f)
            lineTo(19f, 7f)
        }

    /** Lives — the one filled icon in the set, so it reads as a state and not an action. */
    val Heart: ImageVector =
        fill {
            moveTo(12f, 20f)
            curveTo(10f, 18.5f, 5f, 14.5f, 5f, 10.5f)
            curveTo(5f, 8f, 7f, 6.5f, 9f, 6.5f)
            curveTo(10.5f, 6.5f, 11.5f, 7.2f, 12f, 8f)
            curveTo(12.5f, 7.2f, 13.5f, 6.5f, 15f, 6.5f)
            curveTo(17f, 6.5f, 19f, 8f, 19f, 10.5f)
            curveTo(19f, 14.5f, 14f, 18.5f, 12f, 20f)
            close()
        }

    /** Credit — a circle with a plus. */
    val Coin: ImageVector =
        stroke {
            circle(12f, 12f, 8f)
            moveTo(12f, 8f)
            lineTo(12f, 16f)
            moveTo(9f, 12f)
            lineTo(15f, 12f)
        }

    val Box: ImageVector =
        stroke {
            moveTo(4f, 8f)
            lineTo(20f, 8f)
            lineTo(20f, 19f)
            lineTo(4f, 19f)
            close()
            moveTo(4f, 8f)
            lineTo(6f, 4f)
            lineTo(18f, 4f)
            lineTo(20f, 8f)
            moveTo(12f, 4f)
            lineTo(12f, 19f)
        }

    val Users: ImageVector =
        stroke {
            circle(9f, 9f, 3f)
            moveTo(3f, 20f)
            curveTo(3f, 16.5f, 6f, 15f, 9f, 15f)
            curveTo(12f, 15f, 15f, 16.5f, 15f, 20f)
            moveTo(16f, 7f)
            curveTo(17.7f, 7f, 19f, 8.3f, 19f, 10f)
            curveTo(19f, 11.7f, 17.7f, 13f, 16f, 13f)
        }

    val Globe: ImageVector =
        stroke {
            circle(12f, 12f, 9f)
            moveTo(3f, 12f)
            lineTo(21f, 12f)
            ellipse(12f, 12f, 3f, 9f)
        }

    val Sync: ImageVector =
        stroke {
            moveTo(14.736f, 19.518f)
            arcTo(8f, 8f, 0f, false, true, 6.858f, 5.872f)
            moveTo(9.264f, 4.482f)
            arcTo(8f, 8f, 0f, false, true, 17.142f, 18.128f)
            moveTo(17f, 4f)
            lineTo(17f, 8f)
            lineTo(13f, 8f)
            moveTo(7f, 20f)
            lineTo(7f, 16f)
            lineTo(11f, 16f)
        }

    val Logout: ImageVector =
        stroke {
            moveTo(15f, 4f)
            lineTo(18f, 4f)
            lineTo(18f, 20f)
            lineTo(15f, 20f)
            moveTo(10f, 8f)
            lineTo(6f, 12f)
            lineTo(10f, 16f)
            moveTo(6f, 12f)
            lineTo(15f, 12f)
        }

    /** Pure black, the OLED skin — a sun. */
    val Sun: ImageVector =
        stroke {
            circle(12f, 12f, 4f)
            moveTo(12f, 3f)
            lineTo(12f, 5f)
            moveTo(12f, 19f)
            lineTo(12f, 21f)
            moveTo(3f, 12f)
            lineTo(5f, 12f)
            moveTo(19f, 12f)
            lineTo(21f, 12f)
        }

    /** Text size — the letter A. */
    val TypeA: ImageVector =
        stroke {
            moveTo(4f, 20f)
            lineTo(10f, 4f)
            lineTo(16f, 20f)
            moveTo(6.5f, 14f)
            lineTo(13.5f, 14f)
        }

    val Bell: ImageVector =
        stroke {
            moveTo(6f, 15f)
            lineTo(6f, 10f)
            curveTo(6f, 6.7f, 8.7f, 4f, 12f, 4f)
            curveTo(15.3f, 4f, 18f, 6.7f, 18f, 10f)
            lineTo(18f, 15f)
            lineTo(20f, 18f)
            lineTo(4f, 18f)
            close()
            moveTo(10f, 21f)
            lineTo(14f, 21f)
        }

    /** Locked — the beta rows in the shop. */
    val Lock: ImageVector =
        stroke {
            moveTo(5f, 11f)
            lineTo(19f, 11f)
            lineTo(19f, 21f)
            lineTo(5f, 21f)
            close()
            moveTo(8f, 11f)
            lineTo(8f, 8f)
            arcTo(4f, 4f, 0f, false, true, 16f, 8f)
            lineTo(16f, 11f)
        }

    /** Add — a slot to buy. */
    val Plus: ImageVector =
        stroke {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }

    /** Watch — an ad to sit through. */
    val Play: ImageVector =
        stroke {
            moveTo(8f, 5f)
            lineTo(19f, 12f)
            lineTo(8f, 19f)
            close()
        }

    /** A golden life. A gem, because the plain heart is already the ordinary one. */
    val Gem: ImageVector =
        stroke {
            moveTo(12f, 4f)
            lineTo(20f, 10f)
            lineTo(12f, 20f)
            lineTo(4f, 10f)
            close()
            moveTo(4f, 10f)
            lineTo(20f, 10f)
            moveTo(12f, 4f)
            lineTo(8f, 10f)
            lineTo(12f, 20f)
        }

    /**
     * Nolics — a ringed dot.
     *
     * The two currencies must be told apart at a glance and at 14dp, so they differ in shape and
     * not only in colour: colour alone fails for anyone who cannot separate blue from gold.
     */
    val Nolic: ImageVector =
        strokeAndFill(
            stroke = {
                circle(12f, 12f, 7.4f)
            },
            fill = {
                circle(12f, 12f, 2.6f)
            },
        )

    /** Gold — a stack of coins. */
    val GoldStack: ImageVector =
        stroke {
            ellipse(12f, 7.5f, 7f, 2.6f)
            moveTo(5f, 7.5f)
            lineTo(5f, 16.5f)
            arcTo(7f, 2.6f, 0f, false, false, 19f, 16.5f)
            lineTo(19f, 7.5f)
            moveTo(5f, 12f)
            arcTo(7f, 2.6f, 0f, false, false, 19f, 12f)
        }

    /**
     * Open the drawer.
     *
     * Three plain lines. Sliders were used at first and read as filters or settings — the dots on
     * them promise adjustment, not navigation.
     */
    val Menu: ImageVector =
        stroke {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17f)
            lineTo(20f, 17f)
        }

    /**
     * Rating. A five-pointed star, and the only shape that means rating here.
     *
     * The stat row used to borrow the "text size" glyph, which is a letter A — it read as typography
     * settings sitting in a list of counters.
     */
    val Star: ImageVector =
        stroke {
            moveTo(12f, 3.5f)
            lineTo(14.6f, 9.1f)
            lineTo(20.5f, 9.9f)
            lineTo(16.2f, 14.1f)
            lineTo(17.3f, 20.2f)
            lineTo(12f, 17.3f)
            lineTo(6.7f, 20.2f)
            lineTo(7.8f, 14.1f)
            lineTo(3.5f, 9.9f)
            lineTo(9.4f, 9.1f)
            close()
        }

    /** Events — a calendar. */
    val Calendar: ImageVector =
        stroke {
            moveTo(4f, 6f)
            lineTo(20f, 6f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            moveTo(4f, 10f)
            lineTo(20f, 10f)
            moveTo(8f, 3f)
            lineTo(8f, 7f)
            moveTo(16f, 3f)
            lineTo(16f, 7f)
        }

    val Clock: ImageVector =
        stroke {
            circle(12f, 12f, 9f)
            moveTo(12f, 8f)
            lineTo(12f, 12f)
            lineTo(15f, 14f)
        }

    val Home: ImageVector =
        stroke {
            moveTo(4f, 10f)
            lineTo(12f, 4f)
            lineTo(20f, 10f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
        }

    val Bag: ImageVector =
        stroke {
            moveTo(5f, 8f)
            lineTo(19f, 8f)
            lineTo(17.8f, 19f)
            lineTo(6.2f, 19f)
            close()
            moveTo(9f, 8f)
            lineTo(9f, 6f)
            curveTo(9f, 4.3f, 10.3f, 3f, 12f, 3f)
            curveTo(13.7f, 3f, 15f, 4.3f, 15f, 6f)
            lineTo(15f, 8f)
        }

    /** Profile and settings — sliders. */
    val Sliders: ImageVector =
        stroke {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17f)
            lineTo(20f, 17f)
            circle(13.5f, 7f, 1.5f)
            circle(8.5f, 12f, 1.5f)
            circle(15.5f, 17f, 1.5f)
        }
}

/*
 * PathBuilder has no oval or arc-by-angle: it speaks the SVG path grammar, where a round shape is
 * two half arcs. The icons were drafted against Path, which does have them, so they never compiled.
 */

/** Traces a full ellipse as two 180-degree arcs, the way an SVG path would. */
private fun PathBuilder.ellipse(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
) {
    moveTo(cx - rx, cy)
    arcTo(rx, ry, 0f, false, true, cx + rx, cy)
    arcTo(rx, ry, 0f, false, true, cx - rx, cy)
    close()
}

private fun PathBuilder.circle(
    cx: Float,
    cy: Float,
    r: Float,
) = ellipse(cx, cy, r, r)

/**
 * An icon that is partly outlined and partly solid.
 *
 * Needed only where a shape reads wrong as pure outline — the nolic's centre is a dot, and an
 * outlined dot at 14dp is a smudge.
 */
private fun strokeAndFill(
    stroke: PathBuilder.() -> Unit,
    fill: PathBuilder.() -> Unit,
): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathData(stroke),
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).addPath(
        pathData = PathData(fill),
        fill = SolidColor(Color.White),
    ).build()

private fun stroke(block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathData(block),
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

private fun fill(block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathData(block),
        fill = SolidColor(Color.White),
    ).build()
