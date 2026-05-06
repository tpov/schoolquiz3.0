@file:Suppress("FunctionNaming", "MagicNumber", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.internet.profile.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.component.ProfileComponent
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate.ProfileUiState
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileQualification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MESSAGE_AUTO_DISMISS_MS = 3_000L
private const val GIFT_BOX_STREAK_TARGET = 10

@Composable
fun ProfileScreen(
    component: ProfileComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsStateWithLifecycle()
    ProfileView(
        state = state,
        onNicknameChange = component::onNicknameChange,
        onSaveNickname = component::onSaveNickname,
        onRefresh = component::onRefresh,
        modifier = modifier,
    )
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_AUTO_DISMISS_MS)
            component.onMessageShown()
        }
    }
}

@Composable
fun ProfileView(
    state: ProfileUiState,
    onNicknameChange: (String) -> Unit,
    onSaveNickname: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = remember(state.profile) { state.profile.dashboardMetrics() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ProfileDashboardCard(
                profile = state.profile,
                metrics = metrics,
                message = state.message,
                isLoading = state.isLoading,
                isSaving = state.isSaving,
                onRefresh = onRefresh,
            )
        }

        item {
            NicknameCard(
                state = state,
                onNicknameChange = onNicknameChange,
                onSaveNickname = onSaveNickname,
            )
        }

        item {
            AwardsCard(profile = state.profile)
        }

        item {
            ProfileBreakdownCard(
                profile = state.profile,
                metrics = metrics,
            )
        }
    }
}

@Composable
private fun ProfileDashboardCard(
    profile: UserProfile,
    metrics: ProfileDashboardMetrics,
    message: String?,
    isLoading: Boolean,
    isSaving: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DashboardHeader(
                profile = profile,
                metrics = metrics,
                message = message,
                isLoading = isLoading,
                isSaving = isSaving,
                onRefresh = onRefresh,
            )
            MetricStrip(profile = profile, metrics = metrics)
            ProfileCharts(metrics = metrics)
        }
    }
}

@Composable
private fun DashboardHeader(
    profile: UserProfile,
    metrics: ProfileDashboardMetrics,
    message: String?,
    isLoading: Boolean,
    isSaving: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(68.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.nickname,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${profile.status.displayName} · ${metrics.leagueName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (message != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onRefresh, enabled = !isLoading && !isSaving) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "Синхронизировать профиль")
            }
        }
    }
}

@Composable
private fun MetricStrip(
    profile: UserProfile,
    metrics: ProfileDashboardMetrics,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill(
                icon = Icons.Default.Insights,
                label = "Опытнее",
                value = "${metrics.experiencePercentile}%",
                modifier = Modifier.weight(1f),
            )
            MetricPill(
                icon = Icons.Default.EmojiEvents,
                label = "Трофеи",
                value = profile.trophies.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill(
                icon = Icons.Default.Diamond,
                label = "Золото",
                value = formatCompact(profile.gold),
                modifier = Modifier.weight(1f),
            )
            MetricPill(
                icon = Icons.Default.Favorite,
                label = "Жизни",
                value = "${profile.standardHearts}+${profile.goldHearts}",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricPill(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(54.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileCharts(metrics: ProfileDashboardMetrics) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExperienceChartPanel(metrics = metrics)
                RadarChartPanel(metrics = metrics)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ExperienceChartPanel(metrics = metrics, modifier = Modifier.weight(0.86f))
                RadarChartPanel(metrics = metrics, modifier = Modifier.weight(1.14f))
            }
        }
    }
}

@Composable
private fun ExperienceChartPanel(
    metrics: ProfileDashboardMetrics,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExperienceDonutChart(metrics = metrics)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Опыт",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "до ${formatCompact(metrics.nextMilestoneDelta.toLong())} XP",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
            )
            MetricBar(
                label = metrics.leagueName,
                value = metrics.leagueProgress / 100f,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ExperienceDonutChart(metrics: ProfileDashboardMetrics) {
    val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
    val progressColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.size(118.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = "Опытнее ${metrics.experiencePercentile} процентов игроков"
                    },
        ) {
            val strokeWidth = 12.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = metrics.experiencePercentile * 3.6f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${metrics.experiencePercentile}%",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "XP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun RadarChartPanel(
    metrics: ProfileDashboardMetrics,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Профиль",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ProfileRadarChart(metrics = metrics)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChartLegendItem("Игрок", MaterialTheme.colorScheme.tertiary)
            ChartLegendItem("Квалиф.", MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ProfileRadarChart(metrics: ProfileDashboardMetrics) {
    val gridColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f)
    val axisColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.28f)
    val playerColor = MaterialTheme.colorScheme.tertiary
    val qualificationColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .semantics {
                    contentDescription = "Радар профиля: игрок и квалификации"
                },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) * 0.42f
        repeat(4) { index ->
            drawRadarWeb(
                axes = metrics.axes.size,
                center = center,
                radius = radius * ((index + 1) / 4f),
                color = gridColor,
                width = 1.dp.toPx(),
            )
        }
        repeat(metrics.axes.size) { index ->
            val point = radarPoint(center, radius, index, metrics.axes.size, 1f)
            drawLine(axisColor, center, point, strokeWidth = 1.dp.toPx())
        }
        drawRadarPolygon(
            values = metrics.playerValues,
            center = center,
            radius = radius,
            color = playerColor,
        )
        drawRadarPolygon(
            values = metrics.qualificationValues,
            center = center,
            radius = radius,
            color = qualificationColor,
        )
    }
}

@Composable
private fun ChartLegendItem(
    label: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
        )
    }
}

@Composable
private fun NicknameCard(
    state: ProfileUiState,
    onNicknameChange: (String) -> Unit,
    onSaveNickname: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Ник", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.nicknameInput,
                    onValueChange = onNicknameChange,
                    enabled = state.canEditNickname && !state.isSaving,
                    singleLine = true,
                    label = { Text("Ник") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onSaveNickname,
                    enabled = state.canSaveNickname,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = "Сохранить ник")
                    }
                }
            }
        }
    }
}

@Composable
private fun AwardsCard(
    profile: UserProfile,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = "Награды", style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AwardTile(
                    icon = Icons.Default.EmojiEvents,
                    label = "Трофеи",
                    value = profile.trophies.toString(),
                    modifier = Modifier.weight(1f),
                )
                AwardTile(
                    icon = Icons.Default.WorkspacePremium,
                    label = "Логотипы",
                    value = profile.ownedLogos.size.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AwardTile(
                    icon = Icons.Default.CardGiftcard,
                    label = "Коробки",
                    value = profile.boxCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                AwardTile(
                    icon = Icons.Default.Star,
                    label = "Статус",
                    value = profile.status.shortName,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AwardTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(58.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileBreakdownCard(
    profile: UserProfile,
    metrics: ProfileDashboardMetrics,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = "Сводка", style = MaterialTheme.typography.titleMedium)
            }
            MetricBar(
                label = "Экономика · ${formatCompact(profile.nolics)} nolics",
                value = metrics.economyPercent / 100f,
                color = MaterialTheme.colorScheme.tertiary,
            )
            MetricBar(
                label = "Жизни · ${profile.standardHearts}+${profile.goldHearts}",
                value = metrics.heartsPercent / 100f,
                color = MaterialTheme.colorScheme.error,
            )
            MetricBar(
                label = "Квалификация · ${metrics.qualificationPercent}%",
                value = metrics.qualificationPercent / 100f,
                color = MaterialTheme.colorScheme.primary,
            )
            ProfileMetadata(profile = profile)
        }
    }
}

@Composable
private fun ProfileMetadata(profile: UserProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow(Icons.Default.Badge, "UID", profile.uid.ifBlank { "-" })
        InfoRow(
            Icons.Default.Language,
            "Языки",
            profile.knownLanguages.ifEmpty { listOf("-") }.joinToString(", "),
        )
        InfoRow(
            Icons.Default.Verified,
            "Премиум",
            if (profile.premiumUntilMs > 0L) "активен" else "0",
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text = label, modifier = Modifier.width(86.dp), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetricBar(
    label: String,
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${(value.coerceIn(0f, 1f) * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(value.coerceIn(0.02f, 1f))
                        .height(8.dp)
                        .background(color, RoundedCornerShape(100.dp)),
            )
        }
    }
}

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
    val path = Path()
    val points =
        values.mapIndexed { index, value ->
            radarPoint(center, radius, index, values.size, value.coerceIn(0f, 1f))
        }
    points.forEachIndexed { index, point ->
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path = path, color = color.copy(alpha = 0.2f))
    drawPath(path = path, color = color, style = Stroke(width = 2.dp.toPx()))
    points.forEach { point ->
        drawCircle(color = color, radius = 3.dp.toPx(), center = point)
    }
}

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

private data class ProfileDashboardMetrics(
    val axes: List<String>,
    val playerValues: List<Float>,
    val qualificationValues: List<Float>,
    val experiencePercentile: Int,
    val leagueName: String,
    val leagueProgress: Int,
    val nextMilestoneDelta: Int,
    val economyPercent: Int,
    val heartsPercent: Int,
    val qualificationPercent: Int,
)

private fun UserProfile.dashboardMetrics(): ProfileDashboardMetrics {
    val experiencePercent = experiencePercentileEstimate(skillPoints)
    val economyPercent = normalizedLogPercent(nolics + gold * 1_000L, 100_000L)
    val heartsPercent = (((standardHearts + goldHearts * 2).coerceAtMost(7)) / 7f * 100f).roundToInt()
    val rewardPercent = normalizedLogPercent(trophies * 24L + boxCount * 8L + ownedLogos.size * 16L, 720L)
    val collectionPercent = ((ownedLogos.size.coerceAtMost(12) / 12f) * 100f).roundToInt()
    val premiumPercent = if (premiumUntilMs > 0L) 100 else boxStreakDays.percentOf(GIFT_BOX_STREAK_TARGET)
    val qualificationValues =
        listOf(
            qualification.sponsorLevel,
            qualification.testerLevel,
            qualification.translatorLevel,
            qualification.moderatorLevel,
            qualification.adminLevel,
            qualification.developerLevel,
        ).map { (it / 100f).coerceIn(0f, 1f) }
    val qualificationPercent =
        if (qualificationValues.isEmpty()) {
            0
        } else {
            (qualificationValues.average() * 100).roundToInt().coerceIn(0, 100)
        }
    val league = leagueForSkill(skillPoints)
    return ProfileDashboardMetrics(
        axes = listOf("XP", "Жизни", "Баланс", "Награды", "Колл.", "Прем."),
        playerValues =
            listOf(
                experiencePercent,
                heartsPercent,
                economyPercent,
                rewardPercent,
                collectionPercent,
                premiumPercent,
            ).map { it / 100f },
        qualificationValues = qualificationValues,
        experiencePercentile = experiencePercent,
        leagueName = league.name,
        leagueProgress = league.progress,
        nextMilestoneDelta = league.nextMilestoneDelta,
        economyPercent = economyPercent,
        heartsPercent = heartsPercent,
        qualificationPercent = qualificationPercent,
    )
}

private data class ExperienceLeague(
    val name: String,
    val progress: Int,
    val nextMilestoneDelta: Int,
)

private fun leagueForSkill(skillPoints: Int): ExperienceLeague {
    val milestones = listOf(0, 100, 500, 1_500, 5_000, 15_000, 50_000)
    val names = listOf("Старт", "Ученик", "Знаток", "Эксперт", "Мастер", "Легенда")
    val lowerIndex = milestones.indexOfLast { skillPoints >= it }.coerceIn(0, names.lastIndex)
    val lower = milestones[lowerIndex]
    val upper = milestones.getOrElse(lowerIndex + 1) { milestones.last() }
    val progress =
        if (upper == lower) {
            100
        } else {
            (((skillPoints - lower).coerceAtLeast(0)) / (upper - lower).toFloat() * 100f)
                .roundToInt()
                .coerceIn(0, 100)
        }
    return ExperienceLeague(
        name = names[lowerIndex],
        progress = progress,
        nextMilestoneDelta = (upper - skillPoints).coerceAtLeast(0),
    )
}

private fun experiencePercentileEstimate(skillPoints: Int): Int {
    if (skillPoints <= 0) return 0
    return ((ln(skillPoints + 1.0) / ln(50_000.0)) * 100.0)
        .roundToInt()
        .coerceIn(1, 99)
}

private fun normalizedLogPercent(
    value: Long,
    maxValue: Long,
): Int {
    if (value <= 0L) return 0
    return ((ln(value + 1.0) / ln(maxValue + 1.0)) * 100.0)
        .roundToInt()
        .coerceIn(1, 100)
}

private fun Int.percentOf(max: Int): Int = ((coerceIn(0, max) / max.toFloat()) * 100f).roundToInt()

private fun formatCompact(value: Long): String =
    when {
        value >= 1_000_000L -> "${value / 1_000_000L}M"
        value >= 10_000L -> "${value / 1_000L}K"
        else -> value.toString()
    }

private val ProfileStatus.displayName: String
    get() =
        when (this) {
            ProfileStatus.OFFLINE -> "Офлайн"
            ProfileStatus.ANONYMOUS -> "Анонимный профиль"
            ProfileStatus.REGISTERED -> "Зарегистрирован"
            ProfileStatus.VALIDATED -> "Валидирован"
        }

private val ProfileStatus.shortName: String
    get() =
        when (this) {
            ProfileStatus.OFFLINE -> "Offline"
            ProfileStatus.ANONYMOUS -> "Anon"
            ProfileStatus.REGISTERED -> "Reg"
            ProfileStatus.VALIDATED -> "Valid"
        }

@Preview(showBackground = true)
@Composable
@Suppress("UnusedPrivateMember")
private fun ProfileViewPreview() {
    ProfileView(
        state =
            ProfileUiState(
                profile =
                    UserProfile(
                        uid = "preview-user",
                        nickname = "UserPreview",
                        status = ProfileStatus.REGISTERED,
                        avatarUrl = null,
                        knownLanguages = listOf("ru", "en"),
                        createdAtMs = 1L,
                        updatedAtMs = 1L,
                        skillPoints = 1_420,
                        gold = 20L,
                        nolics = 18_500L,
                        standardHearts = 5,
                        goldHearts = 1,
                        qualification =
                            ProfileQualification(
                                sponsorLevel = 20,
                                testerLevel = 100,
                                translatorLevel = 45,
                                moderatorLevel = 0,
                                adminLevel = 0,
                                developerLevel = 101,
                            ),
                        boxCount = 2,
                        boxStreakDays = 7,
                        premiumUntilMs = 1_000L,
                        trophies = 6L,
                        ownedLogos = listOf("gold", "diamond", "phoenix"),
                    ),
            ),
        onNicknameChange = {},
        onSaveNickname = {},
        onRefresh = {},
    )
}
