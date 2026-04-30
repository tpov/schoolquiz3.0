@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import kotlinx.coroutines.isActive
import kotlin.math.sqrt
import kotlin.random.Random

private const val ICON_COUNT = 10
private const val ICON_SIZE_DP = 28
private const val ANGULAR_DAMPING_PER_FRAME = 0.997f
private const val FLOOR_RESTITUTION = 0.55f
private const val FLOOR_FRICTION = 0.92f
private const val IMPULSE_STRENGTH_DP_PER_SEC = 1200f
private const val IMPULSE_RADIUS_DP = 80f
private const val ICON_TINT_ALPHA = 0.18f
private const val DRIFT_SPEED_DP_PER_SEC = 50f
private const val MAX_FRAME_DT_SEC = 0.05f
private const val INITIAL_STAGGER_MS = 8_000L
private const val RESPAWN_DELAY_MIN_MS = 1_500L
private const val RESPAWN_DELAY_MAX_MS = 5_000L

@Suppress("LongParameterList")
private class IconState(
    var iconIndex: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var angularVelocity: Float,
    var alive: Boolean,
    var spawnAtNs: Long,
)

@Suppress("CyclomaticComplexMethod", "FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun FloatingIconsLayer(
    icons: List<ImageVector>,
    modifier: Modifier = Modifier,
) {
    if (icons.isEmpty()) return
    val density = LocalDensity.current
    val iconSizePx = with(density) { ICON_SIZE_DP.dp.toPx() }
    val driftSpeedPx = with(density) { DRIFT_SPEED_DP_PER_SEC.dp.toPx() }
    val impulseStrengthPx = with(density) { IMPULSE_STRENGTH_DP_PER_SEC.dp.toPx() }
    val impulseRadiusPx = with(density) { IMPULSE_RADIUS_DP.dp.toPx() }

    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = ICON_TINT_ALPHA)
    val colorFilter = remember(tint) { ColorFilter.tint(tint) }

    val painters = icons.map { rememberVectorPainter(image = it) }
    val currentIconCount by rememberUpdatedState(icons.size)

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val sprites = remember { mutableListOf<IconState>() }
    var tick by remember { mutableIntStateOf(0) }

    // Reset sprites when the icon list identity changes — a new catalog with a new
    // icon set should replace the floating sprites instead of continuing with stale
    // iconIndex values that point at a different list.
    LaunchedEffect(icons) {
        sprites.clear()
    }

    LaunchedEffect(Unit) {
        var lastFrameNs = 0L
        while (isActive) {
            withFrameNanos { now ->
                val width = containerSize.width.toFloat()
                val height = containerSize.height.toFloat()
                val iconCount = currentIconCount
                if (width <= 0f || height <= 0f || iconCount == 0) {
                    lastFrameNs = now
                    return@withFrameNanos
                }
                if (sprites.isEmpty()) {
                    repeat(ICON_COUNT) {
                        sprites += dormantIcon(now + Random.nextLong(0L, INITIAL_STAGGER_MS) * 1_000_000L)
                    }
                }
                val rawDt = if (lastFrameNs == 0L) 0.016f else (now - lastFrameNs) / 1_000_000_000f
                val dt = rawDt.coerceAtMost(MAX_FRAME_DT_SEC)
                lastFrameNs = now
                stepPhysics(
                    icons = sprites,
                    nowNs = now,
                    dt = dt,
                    width = width,
                    height = height,
                    iconSize = iconSizePx,
                    driftSpeed = driftSpeedPx,
                    iconCount = iconCount,
                )
                tick++
            }
        }
    }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { change ->
                                if (change.pressed && change.positionChanged()) {
                                    applyImpulse(
                                        icons = sprites,
                                        touch = change.position,
                                        radius = impulseRadiusPx,
                                        strength = impulseStrengthPx,
                                        iconSize = iconSizePx,
                                    )
                                }
                            }
                        }
                    }
                },
    ) {
        @Suppress("UNUSED_VARIABLE", "UnusedPrivateProperty")
        val unused = tick
        val sized = IntSize(size.width.toInt(), size.height.toInt())
        if (sized != containerSize) {
            containerSize = sized
        }
        sprites.forEach { sprite ->
            if (!sprite.alive) return@forEach
            val painter = painters[sprite.iconIndex.coerceIn(0, painters.lastIndex)]
            translate(left = sprite.x, top = sprite.y) {
                rotate(
                    degrees = sprite.rotation,
                    pivot = Offset(iconSizePx / 2f, iconSizePx / 2f),
                ) {
                    with(painter) {
                        draw(size = Size(iconSizePx, iconSizePx), colorFilter = colorFilter)
                    }
                }
            }
        }
    }
}

private fun dormantIcon(spawnAtNs: Long): IconState {
    return IconState(
        iconIndex = 0,
        x = 0f,
        y = 0f,
        vx = 0f,
        vy = 0f,
        rotation = 0f,
        angularVelocity = 0f,
        alive = false,
        spawnAtNs = spawnAtNs,
    )
}

private fun spawnFromRandomSide(
    icon: IconState,
    iconCount: Int,
    width: Float,
    height: Float,
    iconSize: Float,
    driftSpeed: Float,
) {
    icon.iconIndex = Random.nextInt(iconCount)
    val side = Random.nextInt(3)
    val rangeX = (width - iconSize).coerceAtLeast(0f)
    val rangeY = (height - iconSize * 2f).coerceAtLeast(0f)
    when (side) {
        0 -> {
            // top: enter going down
            icon.x = Random.nextFloat() * rangeX
            icon.y = -iconSize
            icon.vx = (Random.nextFloat() - 0.5f) * driftSpeed * 1.4f
            icon.vy = (driftSpeed * 0.4f) + Random.nextFloat() * driftSpeed * 0.8f
        }
        1 -> {
            // left: enter going right
            icon.x = -iconSize
            icon.y = Random.nextFloat() * rangeY
            icon.vx = (driftSpeed * 0.4f) + Random.nextFloat() * driftSpeed * 0.8f
            icon.vy = (Random.nextFloat() - 0.5f) * driftSpeed * 1.4f
        }
        else -> {
            // right: enter going left
            icon.x = width
            icon.y = Random.nextFloat() * rangeY
            icon.vx = -((driftSpeed * 0.4f) + Random.nextFloat() * driftSpeed * 0.8f)
            icon.vy = (Random.nextFloat() - 0.5f) * driftSpeed * 1.4f
        }
    }
    icon.rotation = Random.nextFloat() * 360f
    icon.angularVelocity = (Random.nextFloat() - 0.5f) * 60f
    icon.alive = true
    icon.spawnAtNs = 0L
}

private fun scheduleRespawn(
    icon: IconState,
    nowNs: Long,
) {
    icon.alive = false
    val delayMs = Random.nextLong(RESPAWN_DELAY_MIN_MS, RESPAWN_DELAY_MAX_MS)
    icon.spawnAtNs = nowNs + delayMs * 1_000_000L
    icon.vx = 0f
    icon.vy = 0f
    icon.angularVelocity = 0f
}

@Suppress("LongParameterList")
private fun stepPhysics(
    icons: MutableList<IconState>,
    nowNs: Long,
    dt: Float,
    width: Float,
    height: Float,
    iconSize: Float,
    driftSpeed: Float,
    iconCount: Int,
) {
    if (width <= 0f || height <= 0f) return
    val floorY = height - iconSize
    icons.forEach { icon ->
        if (!icon.alive) {
            if (nowNs >= icon.spawnAtNs) {
                spawnFromRandomSide(icon, iconCount, width, height, iconSize, driftSpeed)
            }
            return@forEach
        }
        // no gravity, no linear damping — icons keep momentum, drift forever until they leave screen
        icon.angularVelocity *= ANGULAR_DAMPING_PER_FRAME
        icon.x += icon.vx * dt
        icon.y += icon.vy * dt
        icon.rotation += icon.angularVelocity * dt
        // floor bounces
        if (icon.y > floorY && icon.vy > 0f) {
            icon.y = floorY
            icon.vy = -icon.vy * FLOOR_RESTITUTION
            icon.vx *= FLOOR_FRICTION
            icon.angularVelocity *= 0.7f
        }
        // off-screen on left/right/top → schedule random-delay respawn
        val outOfBounds =
            icon.x < -iconSize * 2f ||
                icon.x > width + iconSize * 2f ||
                icon.y < -iconSize * 4f
        if (outOfBounds) {
            scheduleRespawn(icon, nowNs)
        }
    }
}

private fun applyImpulse(
    icons: MutableList<IconState>,
    touch: Offset,
    radius: Float,
    strength: Float,
    iconSize: Float,
) {
    val r2 = radius * radius
    icons.forEach { icon ->
        if (!icon.alive) return@forEach
        val cx = icon.x + iconSize / 2f
        val cy = icon.y + iconSize / 2f
        val dx = cx - touch.x
        val dy = cy - touch.y
        val dist2 = dx * dx + dy * dy
        if (dist2 < r2) {
            val dist = sqrt(dist2)
            val falloff = if (dist <= 0.01f) 1f else (1f - dist / radius)
            val nx = if (dist > 0.01f) dx / dist else 0f
            val ny = if (dist > 0.01f) dy / dist else -1f
            icon.vx += nx * strength * falloff
            icon.vy += ny * strength * falloff
            icon.angularVelocity += (Random.nextFloat() - 0.5f) * 720f * falloff
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FloatingIconsLayerPreview() {
    SchoolQuizTheme {
        Box(
            modifier =
                Modifier
                    .size(220.dp)
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            FloatingIconsLayer(
                icons =
                    listOf(
                        Icons.Filled.School,
                        Icons.Filled.Calculate,
                        Icons.Filled.Science,
                    ),
            )
        }
    }
}
