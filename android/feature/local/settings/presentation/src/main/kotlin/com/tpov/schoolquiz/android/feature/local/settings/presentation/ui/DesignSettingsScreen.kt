@file:Suppress("FunctionNaming", "MagicNumber", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.local.settings.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignBackground
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignCard
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignScoreProgress
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignStyle
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignDeepSurfaceColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignLightBorderColor
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignModeAccent
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignNeutralBorderColor

@Composable
fun DesignSettingsScreen(
    selectedStyle: SchoolQuizDesignStyle,
    onStyleSelected: (SchoolQuizDesignStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    SchoolQuizDesignBackground(
        isHard = false,
        accentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsHeader()
            }
            items(
                items = SchoolQuizDesignStyle.entries,
                key = { style -> style.name },
            ) { style ->
                DesignStyleOption(
                    style = style,
                    selected = style == selectedStyle,
                    onClick = { onStyleSelected(style) },
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Дизайн",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Выбери оформление приложения",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
    }
}

@Composable
private fun DesignStyleOption(
    style: SchoolQuizDesignStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = schoolQuizDesignModeAccent(isHard = false, style = style)
    val borderColor =
        if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        } else {
            schoolQuizDesignNeutralBorderColor(style)
        }
    SchoolQuizDesignCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        accentColor = accent,
        containerColor = schoolQuizDesignDeepSurfaceColor(style),
        borderColor = borderColor,
        elevated = selected,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = style.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = style.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SelectionMark(selected = selected)
            }
            DesignStylePreview(style = style)
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    val color =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            schoolQuizDesignLightBorderColor()
        }
    Surface(
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        color = color.copy(alpha = if (selected) 0.14f else 0.04f),
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = if (selected) 0.9f else 0.5f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun DesignStylePreview(style: SchoolQuizDesignStyle) {
    val accent = schoolQuizDesignModeAccent(isHard = false, style = style)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(
                    width = 1.dp,
                    color = schoolQuizDesignNeutralBorderColor(style),
                    shape = MaterialTheme.shapes.medium,
                ),
    ) {
        SchoolQuizDesignBackground(
            isHard = false,
            style = style,
            modifier = Modifier.fillMaxSize(),
        ) {}
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            PreviewQuestion(style = style, accent = accent)
            PreviewAnswers(style = style)
            SchoolQuizDesignScoreProgress(
                progress = 0.62f,
                userMarkerProgress = 0.72f,
                leaderMarkerProgress = 0.88f,
                style = style,
            )
        }
    }
}

@Composable
private fun PreviewQuestion(
    style: SchoolQuizDesignStyle,
    accent: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = schoolQuizDesignDeepSurfaceColor(style),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.82f)),
    ) {
        Text(
            text = "Карточка вопроса",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PreviewAnswers(style: SchoolQuizDesignStyle) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewAnswer(
            text = "Ответ",
            modifier = Modifier.weight(1f),
            style = style,
        )
        PreviewAnswer(
            text = "Вариант",
            modifier = Modifier.weight(1f),
            style = style,
        )
    }
}

@Composable
private fun PreviewAnswer(
    text: String,
    modifier: Modifier = Modifier,
    style: SchoolQuizDesignStyle,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color =
            MaterialTheme.colorScheme.surface
                .copy(alpha = 0.58f)
                .compositeOver(Color.Black),
        border = BorderStroke(1.dp, schoolQuizDesignLightBorderColor(style)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val SchoolQuizDesignStyle.title: String
    get() =
        when (this) {
            SchoolQuizDesignStyle.Main -> "Основной"
            SchoolQuizDesignStyle.Clean -> "Чистый"
        }

private val SchoolQuizDesignStyle.caption: String
    get() =
        when (this) {
            SchoolQuizDesignStyle.Main -> "Тёмные карточки, белые контуры и цветной акцент"
            SchoolQuizDesignStyle.Clean -> "Тише фон, мягче меню и больше воздуха"
        }

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DesignSettingsScreenPreview() {
    SchoolQuizTheme {
        DesignSettingsScreen(
            selectedStyle = SchoolQuizDesignStyle.Clean,
            onStyleSelected = {},
        )
    }
}
