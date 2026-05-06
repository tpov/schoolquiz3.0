package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.BrandCard
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignChip
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignStyle
import com.tpov.schoolquiz.android.core.designsystem.components.StarRating
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignModeAccent
import com.tpov.schoolquiz.android.core.designsystem.currentSchoolQuizDesignStyle
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
import java.util.Locale

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun LessonItemCard(
    item: LessonItemUi,
    onClick: () -> Unit,
    onHardCheckChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val designStyle = currentSchoolQuizDesignStyle()
    val isClean = designStyle == SchoolQuizDesignStyle.Clean
    BrandCard(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .height(if (isClean) 72.dp else 64.dp)
                    .padding(
                        horizontal = if (isClean) 16.dp else 12.dp,
                        vertical = if (isClean) 12.dp else 8.dp,
                    ),
        ) {
            if (item.orderLabel != null) {
                Text(
                    text = item.orderLabel,
                    style = if (isClean) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isClean) 0.5f else 0.62f),
                    modifier = Modifier.padding(end = if (isClean) 10.dp else 8.dp),
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (item.hardUnlocked) {
                SchoolQuizDesignChip(
                    text = if (item.isHardChecked) "Сложный" else "Легкий",
                    color = schoolQuizDesignModeAccent(isHard = item.isHardChecked),
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .clickable { onHardCheckChanged(!item.isHardChecked) },
                )
            }
            StarRating(
                rating = item.averageRating,
                modifier = Modifier.padding(start = 8.dp),
                size = if (isClean) 24.dp else 28.dp,
                tint = MaterialTheme.colorScheme.secondary,
            )
            if (item.averageRating != null) {
                Text(
                    text = String.format(Locale.US, "%.1f", item.averageRating),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 2.dp),
                )
                if (item.ratingCount > 0) {
                    Text(
                        text = "(${item.ratingCount})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun LessonItemCardBasicPreview() {
    SchoolQuizTheme {
        LessonItemCard(
            item =
                LessonItemUi(
                    id = "l1",
                    title = "Урок 1 — Введение в алгебру",
                    orderLabel = "1.",
                    averageRating = 2.0f,
                    ratingCount = 12,
                    hardUnlocked = false,
                    isDownloaded = true,
                ),
            onClick = {},
            onHardCheckChanged = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun LessonItemCardHardUnlockedPreview() {
    SchoolQuizTheme {
        LessonItemCard(
            item =
                LessonItemUi(
                    id = "l2",
                    title = "Урок 2 — Сложные задачи с длинным названием которое не помещается",
                    orderLabel = "2.",
                    averageRating = 2.7f,
                    ratingCount = 8,
                    hardUnlocked = true,
                    isHardChecked = true,
                    isDownloaded = false,
                ),
            onClick = {},
            onHardCheckChanged = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun LessonItemCardZeroStarsPreview() {
    SchoolQuizTheme {
        LessonItemCard(
            item =
                LessonItemUi(
                    id = "l3",
                    title = "Урок 3 — Новый урок (нет попыток)",
                    bestStarsRawTenths = 0,
                    hardUnlocked = false,
                    isDownloading = true,
                ),
            onClick = {},
            onHardCheckChanged = {},
        )
    }
}
